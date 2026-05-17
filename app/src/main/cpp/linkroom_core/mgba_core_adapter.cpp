#include "mgba_core_adapter.h"

#include <mgba/core/blip_buf.h>
#include <mgba/core/core.h>
#include <mgba/core/input.h>
#include <mgba/core/interface.h>
#include <mgba/core/serialize.h>
#include <mgba/internal/gba/audio.h>
#include <mgba/internal/gba/input.h>
#include <mgba-util/vfs.h>

#include <android/log.h>
#include <android/native_window.h>
#include <chrono>
#include <cerrno>
#include <cstring>
#include <cstdlib>
#include <fstream>
#include <fcntl.h>
#include <algorithm>
#include <sstream>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include "video_frame_buffer.h"

namespace linkroom {
namespace {
constexpr const char* kTag = "MgbaCoreAdapter";
constexpr int kBootProbeFrames = 5;
constexpr int kVideoStride = 256;
constexpr int kAudioSampleRate = 48000;
constexpr size_t kAudioBufferSamples = kAudioSampleRate * 2 * 2;
constexpr size_t kMaxAudioDrainFrames = 4096;

bool fileExists(const std::string& path) {
    struct stat info {};
    return !path.empty() && stat(path.c_str(), &info) == 0 && S_ISREG(info.st_mode);
}

long fileSize(const std::string& path) {
    struct stat info {};
    if (path.empty() || stat(path.c_str(), &info) != 0 || !S_ISREG(info.st_mode)) {
        return 0;
    }
    return static_cast<long>(info.st_size);
}

bool directoryExists(const std::string& path) {
    struct stat info {};
    return !path.empty() && stat(path.c_str(), &info) == 0 && S_ISDIR(info.st_mode);
}

bool ensureDirectory(const std::string& path) {
    if (path.empty() || directoryExists(path)) {
        return !path.empty();
    }

    std::string current;
    size_t start = 0;
    if (path[0] == '/') {
        current = "/";
        start = 1;
    }

    while (start <= path.size()) {
        const size_t end = path.find('/', start);
        const std::string part = path.substr(start, end == std::string::npos ? std::string::npos : end - start);
        if (!part.empty()) {
            if (!current.empty() && current.back() != '/') {
                current += "/";
            }
            current += part;
            if (!directoryExists(current) && mkdir(current.c_str(), 0700) != 0 && errno != EEXIST) {
                return false;
            }
        }
        if (end == std::string::npos) {
            break;
        }
        start = end + 1;
    }
    return directoryExists(path);
}

std::vector<std::uint8_t> readFile(const std::string& path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        return {};
    }
    input.seekg(0, std::ios::end);
    const auto size = input.tellg();
    if (size <= 0) {
        return {};
    }
    input.seekg(0, std::ios::beg);
    std::vector<std::uint8_t> data(static_cast<size_t>(size));
    input.read(reinterpret_cast<char*>(data.data()), static_cast<std::streamsize>(data.size()));
    if (!input) {
        return {};
    }
    return data;
}

bool copyFile(const std::string& from, const std::string& to) {
    std::ifstream input(from, std::ios::binary);
    std::ofstream output(to, std::ios::binary | std::ios::trunc);
    if (!input || !output) {
        return false;
    }
    output << input.rdbuf();
    return output.good();
}

std::int64_t nowMillis() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();
}

std::string parentDirectory(const std::string& path) {
    const size_t separator = path.find_last_of('/');
    if (separator == std::string::npos) {
        return {};
    }
    if (separator == 0) {
        return "/";
    }
    return path.substr(0, separator);
}
}

MgbaCoreAdapter::~MgbaCoreAdapter() {
    release();
}

bool MgbaCoreAdapter::isCoreAvailable() const {
    static_assert(mPLATFORM_GBA == 0, "Unexpected mGBA platform enum layout.");
    static_assert(GBA_KEY_A == 0, "Unexpected mGBA input enum layout.");
    static_assert(GBA_KEY_B == 1, "Unexpected mGBA B key enum layout.");
    static_assert(GBA_KEY_SELECT == 2, "Unexpected mGBA Select key enum layout.");
    static_assert(GBA_KEY_START == 3, "Unexpected mGBA Start key enum layout.");
    static_assert(GBA_KEY_RIGHT == 4, "Unexpected mGBA Right key enum layout.");
    static_assert(GBA_KEY_LEFT == 5, "Unexpected mGBA Left key enum layout.");
    static_assert(GBA_KEY_UP == 6, "Unexpected mGBA Up key enum layout.");
    static_assert(GBA_KEY_DOWN == 7, "Unexpected mGBA Down key enum layout.");
    static_assert(GBA_KEY_R == 8, "Unexpected mGBA R key enum layout.");
    static_assert(GBA_KEY_L == 9, "Unexpected mGBA L key enum layout.");
    struct mCore* core = mCoreCreate(mPLATFORM_GBA);
    if (core == nullptr) {
        return false;
    }
    core->deinit(core);
    return true;
}

std::string MgbaCoreAdapter::statusMessage() const {
    if (romLoaded_ && !paused_ && fastForwardEnabled_) {
        return "running: mGBA fast-forward is active";
    }
    if (romLoaded_ && !paused_) {
        return "running: mGBA video frames are rendering";
    }
    if (romLoaded_ && paused_) {
        return "paused: mGBA core is loaded";
    }
    return linkedCoreStatus();
}

std::string MgbaCoreAdapter::linkedCoreStatus() const {
    return isCoreAvailable()
        ? "mGBA core linked: true (0.10.5, GBA core compiled; video/audio rendering enabled)"
        : "mGBA core linked: false";
}

RomLoadResult MgbaCoreAdapter::loadAndBootGba(const std::string& romPath, const SavePaths& savePaths) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "Loading ROM from private path: %s", romPath.c_str());
    release();

    if (!fileExists(romPath)) {
        return {
            RomLoadStatus::FileNotFound,
            "file not found: copied ROM is missing from app-private storage"
        };
    }

    gameRootDirectory_ = savePaths.gameRoot();
    batteryDirectory_ = savePaths.batteryDirectory();
    batterySavePath_ = savePaths.currentBatterySave();
    __android_log_print(ANDROID_LOG_INFO, kTag, "Save game root: %s", savePaths.gameRoot().c_str());
    __android_log_print(ANDROID_LOG_INFO, kTag, "Expected battery directory: %s", batteryDirectory_.c_str());
    __android_log_print(ANDROID_LOG_INFO, kTag, "Expected battery save file: %s", batterySavePath_.c_str());
    const bool saveExistedBeforeBoot = fileExists(batterySavePath_);
    const long saveSizeBeforeBoot = fileSize(batterySavePath_);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "Battery save before boot: exists=%s size=%ld",
        saveExistedBeforeBoot ? "true" : "false",
        saveSizeBeforeBoot
    );
    const bool saveDirectoryReady = ensureDirectory(batteryDirectory_);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "Battery directory create/check result: %s",
        saveDirectoryReady ? "success" : "failure"
    );
    if (!saveDirectoryReady) {
        saveStatus_ = "save flush failed: unable to prepare battery save directory";
        __android_log_print(ANDROID_LOG_WARN, kTag, "%s: %s", saveStatus_.c_str(), batteryDirectory_.c_str());
    } else {
        __android_log_print(ANDROID_LOG_INFO, kTag, "Battery save path: %s", batterySavePath_.c_str());
    }

    core_ = mCoreCreate(mPLATFORM_GBA);
    if (core_ == nullptr) {
        return {
            RomLoadStatus::UnexpectedNativeError,
            "unexpected native error: unable to create mGBA GBA core"
        };
    }

    if (!core_->init(core_)) {
        release();
        return {
            RomLoadStatus::UnexpectedNativeError,
            "unexpected native error: unable to initialize mGBA core"
        };
    }

    // mGBA expects config and video/audio buffers before normal frame execution.
    // Video is rendered into the Android SurfaceView. Audio is drained into a
    // small native ring buffer for Kotlin AudioTrack playback.
    mCoreInitConfig(core_, "linkroom");
    videoBuffer_.assign(kVideoStride * VideoFrameBuffer::kGbaHeight, 0);
    core_->setVideoBuffer(core_, reinterpret_cast<color_t*>(videoBuffer_.data()), kVideoStride);
    configureAudio();
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "Configured mGBA video buffer: %d x %d, stride %d",
        VideoFrameBuffer::kGbaWidth,
        VideoFrameBuffer::kGbaHeight,
        kVideoStride
    );

    VFile* rom = VFileOpen(romPath.c_str(), O_RDONLY);
    if (rom == nullptr) {
        const int error = errno;
        release();
        return {
            RomLoadStatus::FileNotFound,
            std::string("file not found: unable to open copied ROM (") + std::strerror(error) + ")"
        };
    }

    if (!core_->isROM(rom)) {
        rom->close(rom);
        release();
        return {
            RomLoadStatus::InvalidRom,
            "invalid ROM: mGBA did not recognize this file as a GBA ROM"
        };
    }
    rom->seek(rom, 0, SEEK_SET);

    if (!core_->loadROM(core_, rom)) {
        rom->close(rom);
        release();
        return {
            RomLoadStatus::MgbaLoadFailure,
            "mGBA load failure: the ROM was recognized but could not be loaded"
        };
    }
    romLoaded_ = true;

    const bool saveLoadResult = !batterySavePath_.empty() && mCoreLoadSaveFile(core_, batterySavePath_.c_str(), false);
    const bool saveExistsAfterAttach = fileExists(batterySavePath_);
    const long saveSizeAfterAttach = fileSize(batterySavePath_);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "mGBA file-backed save attach result: %s; existsAfterAttach=%s sizeAfterAttach=%ld",
        saveLoadResult ? "success" : "failure",
        saveExistsAfterAttach ? "true" : "false",
        saveSizeAfterAttach
    );
    if (!saveLoadResult) {
        saveStatus_ = "save load failed: mGBA could not attach current.sav";
    } else if (saveExistedBeforeBoot && saveSizeBeforeBoot > 0) {
        saveStatus_ = "save loaded: current.sav";
    } else {
        saveStatus_ = "no save found: a new battery save will be created after in-game save";
    }
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", saveStatus_.c_str());

    core_->reset(core_);
    for (int frame = 0; frame < kBootProbeFrames; ++frame) {
        core_->runFrame(core_);
        drainAudio();
    }
    paused_ = false;
    if (audioConfigured_) {
        audioStatus_ = "audio running: 48000 Hz stereo PCM16";
    }

    __android_log_print(ANDROID_LOG_INFO, kTag, "ROM load succeeded; video rendering can start.");
    return {
        RomLoadStatus::Success,
        "running: mGBA loaded and started video rendering"
    };
}

bool MgbaCoreAdapter::runFrame() {
    if (core_ == nullptr || !romLoaded_ || paused_) {
        return false;
    }

    core_->setKeys(core_, inputMask_);
    core_->runFrame(core_);
    drainAudio();
    return true;
}

void MgbaCoreAdapter::configureAudio() {
    if (core_ == nullptr) {
        audioStatus_ = "audio unavailable: mGBA core is not initialized";
        return;
    }

    core_->setAudioBufferSize(core_, kMaxAudioDrainFrames);
    const double ratio = GBAAudioCalculateRatio(1.0f, 60.0f, 1.0f);
    blip_t* left = core_->getAudioChannel(core_, 0);
    blip_t* right = core_->getAudioChannel(core_, 1);
    if (left == nullptr || right == nullptr) {
        audioStatus_ = "audio unavailable: mGBA audio channels are missing";
        __android_log_print(ANDROID_LOG_WARN, kTag, "%s", audioStatus_.c_str());
        return;
    }

    blip_set_rates(left, core_->frequency(core_), kAudioSampleRate * ratio);
    blip_set_rates(right, core_->frequency(core_), kAudioSampleRate * ratio);
    audioRingBuffer_.assign(kAudioBufferSamples, 0);
    audioReadIndex_ = 0;
    audioWriteIndex_ = 0;
    audioBufferedSamples_ = 0;
    audioOverruns_ = 0;
    audioUnderruns_ = 0;
    audioConfigured_ = true;
    audioStatus_ = "audio initialized: 48000 Hz stereo PCM16";
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "Audio init: sampleRate=%d ringBufferSamples=%zu blipRatio=%.6f",
        kAudioSampleRate,
        audioRingBuffer_.size(),
        ratio
    );
}

void MgbaCoreAdapter::drainAudio() {
    if (!audioConfigured_ || core_ == nullptr || audioRingBuffer_.empty()) {
        return;
    }

    blip_t* left = core_->getAudioChannel(core_, 0);
    blip_t* right = core_->getAudioChannel(core_, 1);
    if (left == nullptr || right == nullptr) {
        audioStatus_ = "audio failed: mGBA audio channel disappeared";
        return;
    }

    const int leftAvailable = blip_samples_avail(left);
    const int rightAvailable = blip_samples_avail(right);
    const int framesAvailable = std::min({leftAvailable, rightAvailable, static_cast<int>(kMaxAudioDrainFrames)});
    if (framesAvailable <= 0) {
        return;
    }

    if (fastForwardEnabled_) {
        std::vector<std::int16_t> discarded(static_cast<size_t>(framesAvailable) * 2);
        const int produced = blip_read_samples(left, discarded.data(), framesAvailable, true);
        blip_read_samples(right, discarded.data() + 1, produced, true);
        return;
    }

    std::vector<std::int16_t> interleaved(static_cast<size_t>(framesAvailable) * 2);
    const int produced = blip_read_samples(left, interleaved.data(), framesAvailable, true);
    blip_read_samples(right, interleaved.data() + 1, produced, true);
    pushAudioSamples(interleaved.data(), static_cast<size_t>(produced) * 2);
}

void MgbaCoreAdapter::pushAudioSamples(const std::int16_t* samples, size_t sampleCount) {
    if (samples == nullptr || sampleCount == 0 || audioRingBuffer_.empty()) {
        return;
    }

    const size_t capacity = audioRingBuffer_.size();
    if (sampleCount > capacity) {
        samples += sampleCount - capacity;
        sampleCount = capacity;
    }

    const size_t freeSamples = capacity - audioBufferedSamples_;
    if (sampleCount > freeSamples) {
        const size_t samplesToDrop = sampleCount - freeSamples;
        audioReadIndex_ = (audioReadIndex_ + samplesToDrop) % capacity;
        audioBufferedSamples_ -= samplesToDrop;
        ++audioOverruns_;
        if (audioOverruns_ <= 5 || audioOverruns_ % 120 == 0) {
            __android_log_print(
                ANDROID_LOG_WARN,
                kTag,
                "Audio overrun: droppedSamples=%zu overruns=%zu",
                samplesToDrop,
                audioOverruns_
            );
        }
    }

    for (size_t i = 0; i < sampleCount; ++i) {
        audioRingBuffer_[audioWriteIndex_] = samples[i];
        audioWriteIndex_ = (audioWriteIndex_ + 1) % capacity;
    }
    audioBufferedSamples_ += sampleCount;
}

int MgbaCoreAdapter::readAudio(std::int16_t* output, int maxSamples) {
    if (output == nullptr || maxSamples <= 0 || !audioConfigured_ || audioRingBuffer_.empty() || fastForwardEnabled_) {
        return 0;
    }

    const size_t capacity = audioRingBuffer_.size();
    const size_t samplesToRead = std::min(static_cast<size_t>(maxSamples), audioBufferedSamples_);
    if (samplesToRead == 0) {
        ++audioUnderruns_;
        if (audioUnderruns_ <= 5 || audioUnderruns_ % 240 == 0) {
            __android_log_print(ANDROID_LOG_DEBUG, kTag, "Audio underrun: count=%zu", audioUnderruns_);
        }
        return 0;
    }

    for (size_t i = 0; i < samplesToRead; ++i) {
        output[i] = audioRingBuffer_[audioReadIndex_];
        audioReadIndex_ = (audioReadIndex_ + 1) % capacity;
    }
    audioBufferedSamples_ -= samplesToRead;
    return static_cast<int>(samplesToRead);
}

void MgbaCoreAdapter::resetAudioBuffer() {
    audioRingBuffer_.clear();
    audioReadIndex_ = 0;
    audioWriteIndex_ = 0;
    audioBufferedSamples_ = 0;
    audioOverruns_ = 0;
    audioUnderruns_ = 0;
    audioConfigured_ = false;
}

bool MgbaCoreAdapter::renderFrameToWindow(ANativeWindow* window, int windowWidth, int windowHeight) {
    if (window == nullptr || windowWidth <= 0 || windowHeight <= 0 || videoBuffer_.empty()) {
        return false;
    }

    if (ANativeWindow_setBuffersGeometry(window, windowWidth, windowHeight, WINDOW_FORMAT_RGBA_8888) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Unable to set native window geometry for mGBA frame.");
        return false;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, nullptr) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Unable to lock native window for mGBA frame.");
        return false;
    }

    auto* destination = static_cast<std::uint32_t*>(buffer.bits);
    if (destination == nullptr || buffer.stride <= 0 || buffer.width <= 0 || buffer.height <= 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Native window buffer is invalid for mGBA frame.");
        ANativeWindow_unlockAndPost(window);
        return false;
    }

    const int bufferWidth = buffer.width;
    const int bufferHeight = buffer.height;
    const int destinationStride = buffer.stride;
    const int sourceWidth = VideoFrameBuffer::kGbaWidth;
    const int sourceHeight = VideoFrameBuffer::kGbaHeight;
    const float sourceAspect = static_cast<float>(sourceWidth) / static_cast<float>(sourceHeight);
    const float bufferAspect = static_cast<float>(bufferWidth) / static_cast<float>(bufferHeight);

    int drawWidth = bufferWidth;
    int drawHeight = bufferHeight;
    if (bufferAspect > sourceAspect) {
        drawWidth = static_cast<int>(bufferHeight * sourceAspect);
    } else {
        drawHeight = static_cast<int>(bufferWidth / sourceAspect);
    }
    drawWidth = std::max(1, std::min(drawWidth, bufferWidth));
    drawHeight = std::max(1, std::min(drawHeight, bufferHeight));
    const int offsetX = (bufferWidth - drawWidth) / 2;
    const int offsetY = (bufferHeight - drawHeight) / 2;

    for (int y = 0; y < bufferHeight; ++y) {
        std::uint32_t* row = destination + y * destinationStride;
        std::fill(row, row + bufferWidth, 0xFF000000u);
    }

    for (int y = 0; y < drawHeight; ++y) {
        const int sourceY = (y * sourceHeight) / drawHeight;
        std::uint32_t* destinationRow = destination + (offsetY + y) * destinationStride + offsetX;
        const std::uint32_t* sourceRow = videoBuffer_.data() + sourceY * kVideoStride;
        for (int x = 0; x < drawWidth; ++x) {
            const int sourceX = (x * sourceWidth) / drawWidth;
            destinationRow[x] = sourceRow[sourceX] | 0xFF000000u;
        }
    }

    ANativeWindow_unlockAndPost(window);
    return true;
}

void MgbaCoreAdapter::setInputMask(std::uint32_t inputMask) {
    inputMask_ = inputMask;
    if (core_ != nullptr && romLoaded_) {
        core_->setKeys(core_, inputMask_);
    }
}

void MgbaCoreAdapter::setFastForward(bool enabled) {
    fastForwardEnabled_ = enabled;
    if (enabled) {
        audioReadIndex_ = 0;
        audioWriteIndex_ = 0;
        audioBufferedSamples_ = 0;
        audioStatus_ = "audio muted: fast-forward is active";
    } else if (audioConfigured_) {
        audioStatus_ = paused_ ? "audio paused" : "audio running: 48000 Hz stereo PCM16";
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "Fast-forward %s at 2x; audioMuted=%s",
        enabled ? "enabled" : "disabled",
        enabled ? "true" : "false"
    );
}

std::string MgbaCoreAdapter::saveStateToFile(int slot, const std::string& statePath) {
    if (core_ == nullptr || !romLoaded_) {
        return "state save failed: no active ROM";
    }
    if (slot < 1 || slot > 3) {
        return "state save failed: invalid slot";
    }
    if (statePath.empty()) {
        return "state save failed: empty state path";
    }

    const std::string directory = parentDirectory(statePath);
    if (directory.empty() || !ensureDirectory(directory)) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "State save failed: unable to prepare directory for slot %d: %s", slot, directory.c_str());
        return "state save failed: unable to prepare state directory";
    }

    __android_log_print(ANDROID_LOG_INFO, kTag, "Saving state: slot=%d path=%s", slot, statePath.c_str());
    VFile* stateFile = VFileOpen(statePath.c_str(), O_CREAT | O_TRUNC | O_RDWR);
    if (stateFile == nullptr) {
        const int error = errno;
        __android_log_print(ANDROID_LOG_WARN, kTag, "State save open failed: slot=%d path=%s error=%s", slot, statePath.c_str(), std::strerror(error));
        return std::string("state save failed: unable to open slot file (") + std::strerror(error) + ")";
    }

    const bool success = mCoreSaveStateNamed(core_, stateFile, SAVESTATE_RTC | SAVESTATE_METADATA);
    stateFile->close(stateFile);
    if (!success) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "State save failed: slot=%d path=%s", slot, statePath.c_str());
        return "state save failed: mGBA could not write state";
    }

    __android_log_print(ANDROID_LOG_INFO, kTag, "State save succeeded: slot=%d path=%s size=%ld", slot, statePath.c_str(), fileSize(statePath));
    return "state saved: Slot " + std::to_string(slot);
}

std::string MgbaCoreAdapter::loadStateFromFile(int slot, const std::string& statePath) {
    if (core_ == nullptr || !romLoaded_) {
        return "state load failed: no active ROM";
    }
    if (slot < 1 || slot > 3) {
        return "state load failed: invalid slot";
    }
    if (!fileExists(statePath)) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "State load ignored: empty slot=%d path=%s", slot, statePath.c_str());
        return "state load failed: Slot " + std::to_string(slot) + " is empty";
    }

    __android_log_print(ANDROID_LOG_INFO, kTag, "Loading state: slot=%d path=%s size=%ld", slot, statePath.c_str(), fileSize(statePath));
    VFile* stateFile = VFileOpen(statePath.c_str(), O_RDONLY);
    if (stateFile == nullptr) {
        const int error = errno;
        __android_log_print(ANDROID_LOG_WARN, kTag, "State load open failed: slot=%d path=%s error=%s", slot, statePath.c_str(), std::strerror(error));
        return std::string("state load failed: unable to open slot file (") + std::strerror(error) + ")";
    }

    const bool success = mCoreLoadStateNamed(core_, stateFile, SAVESTATE_RTC | SAVESTATE_METADATA);
    stateFile->close(stateFile);
    audioReadIndex_ = 0;
    audioWriteIndex_ = 0;
    audioBufferedSamples_ = 0;
    if (!success) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "State load failed: slot=%d path=%s", slot, statePath.c_str());
        return "state load failed: mGBA could not read state";
    }

    __android_log_print(ANDROID_LOG_INFO, kTag, "State load succeeded: slot=%d path=%s", slot, statePath.c_str());
    return "state loaded: Slot " + std::to_string(slot);
}

std::string MgbaCoreAdapter::flushBatterySave() {
    if (core_ == nullptr || !romLoaded_) {
        saveStatus_ = "save not flushed: no active ROM";
        return saveStatus_;
    }
    if (batterySavePath_.empty() || batteryDirectory_.empty() || !ensureDirectory(batteryDirectory_)) {
        saveStatus_ = "save flush failed: battery save directory unavailable";
        __android_log_print(ANDROID_LOG_WARN, kTag, "%s", saveStatus_.c_str());
        return saveStatus_;
    }

    void* saveData = nullptr;
    const size_t saveSize = core_->savedataClone(core_, &saveData);
    if (saveSize == 0 || saveData == nullptr) {
        const bool existingFile = fileExists(batterySavePath_);
        const long existingSize = fileSize(batterySavePath_);
        if (existingFile && existingSize > 0) {
            saveStatus_ = "save already file-backed: current.sav exists";
        } else {
            saveStatus_ = "save not flushed: this game has no battery save data yet";
        }
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "%s; current.sav exists=%s size=%ld",
            saveStatus_.c_str(),
            existingFile ? "true" : "false",
            existingSize
        );
        if (saveData != nullptr) {
            std::free(saveData);
        }
        return saveStatus_;
    }

    if (fileExists(batterySavePath_)) {
        const std::string backupPath = batteryDirectory_ + "/" + SavePaths::kBatteryBackupPrefix +
            std::to_string(nowMillis()) + ".sav";
        if (copyFile(batterySavePath_, backupPath)) {
            __android_log_print(ANDROID_LOG_INFO, kTag, "Created battery save backup: %s", backupPath.c_str());
        } else {
            __android_log_print(ANDROID_LOG_WARN, kTag, "Unable to create battery save backup before flush.");
        }
    }

    const std::string tempPath = batterySavePath_ + ".tmp";
    bool wrote = false;
    {
        std::ofstream output(tempPath, std::ios::binary | std::ios::trunc);
        if (output) {
            output.write(static_cast<const char*>(saveData), static_cast<std::streamsize>(saveSize));
            wrote = output.good();
        }
    }
    std::free(saveData);

    if (!wrote || rename(tempPath.c_str(), batterySavePath_.c_str()) != 0) {
        unlink(tempPath.c_str());
        saveStatus_ = "save flush failed: unable to write current.sav";
        __android_log_print(ANDROID_LOG_WARN, kTag, "%s", saveStatus_.c_str());
        return saveStatus_;
    }

    std::ostringstream message;
    message << "save flushed: current.sav (" << saveSize << " bytes)";
    saveStatus_ = message.str();
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "%s; gamesDirExists=%s batteryDirExists=%s currentExists=%s currentSize=%ld",
        saveStatus_.c_str(),
        directoryExists(gameRootDirectory_.substr(0, gameRootDirectory_.find_last_of('/'))) ? "true" : "false",
        directoryExists(batteryDirectory_) ? "true" : "false",
        fileExists(batterySavePath_) ? "true" : "false",
        fileSize(batterySavePath_)
    );
    return saveStatus_;
}

void MgbaCoreAdapter::pause() {
    paused_ = true;
    if (audioConfigured_) {
        audioStatus_ = "audio paused";
    }
}

void MgbaCoreAdapter::resume() {
    if (core_ != nullptr) {
        paused_ = false;
        if (audioConfigured_) {
            audioStatus_ = "audio running: 48000 Hz stereo PCM16";
        }
    }
}

void MgbaCoreAdapter::release() {
    if (core_ != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "Releasing mGBA core.");
        flushBatterySave();
        if (romLoaded_) {
            core_->unloadROM(core_);
        }
        core_->deinit(core_);
        core_ = nullptr;
    }
    videoBuffer_.clear();
    resetAudioBuffer();
    gameRootDirectory_.clear();
    batterySavePath_.clear();
    batteryDirectory_.clear();
    inputMask_ = 0;
    romLoaded_ = false;
    paused_ = true;
    fastForwardEnabled_ = false;
    audioStatus_ = "audio released";
}

bool MgbaCoreAdapter::hasLoadedRom() const {
    return romLoaded_;
}

bool MgbaCoreAdapter::isPaused() const {
    return paused_;
}

std::string MgbaCoreAdapter::saveStatus() const {
    return saveStatus_;
}

std::string MgbaCoreAdapter::audioStatus() const {
    if (!audioConfigured_) {
        return audioStatus_;
    }

    std::ostringstream message;
    message << audioStatus_
            << " (bufferedSamples=" << audioBufferedSamples_
            << ", underruns=" << audioUnderruns_
            << ", overruns=" << audioOverruns_
            << ")";
    return message.str();
}

bool MgbaCoreAdapter::isFastForwardEnabled() const {
    return fastForwardEnabled_;
}

std::string MgbaCoreAdapter::fastForwardStatus() const {
    if (!romLoaded_) {
        return "fast-forward: waiting for ROM";
    }
    if (fastForwardEnabled_) {
        return "fast-forward: on (2x, audio muted)";
    }
    return "fast-forward: off";
}

} // namespace linkroom
