cmake_minimum_required(VERSION 3.22.1)

project(linkroom_native)

set(MGBA_UPSTREAM_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/mgba/upstream")

# Phase 0.2C1 mGBA build:
# Build a minimal static mGBA library from the pinned upstream source. This
# links actual mGBA implementation code into linkroom_native. Runtime loads
# user-copied .gba files and renders software video frames to SurfaceView.
set(LIBMGBA_ONLY ON CACHE BOOL "" FORCE)
set(BINARY_NAME linkroom_mgba CACHE STRING "" FORCE)
set(M_CORE_GBA ON CACHE BOOL "" FORCE)
set(M_CORE_GB OFF CACHE BOOL "" FORCE)
set(MINIMAL_CORE 2 CACHE STRING "" FORCE)
set(ENABLE_EXTRA ON CACHE BOOL "" FORCE)
set(DISABLE_DEPS ON CACHE BOOL "" FORCE)
set(DISABLE_FRONTENDS ON CACHE BOOL "" FORCE)
set(BUILD_STATIC ON CACHE BOOL "" FORCE)
set(BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(BUILD_QT OFF CACHE BOOL "" FORCE)
set(BUILD_SDL OFF CACHE BOOL "" FORCE)
set(BUILD_LIBRETRO OFF CACHE BOOL "" FORCE)
set(BUILD_PERF OFF CACHE BOOL "" FORCE)
set(BUILD_TEST OFF CACHE BOOL "" FORCE)
set(BUILD_SUITE OFF CACHE BOOL "" FORCE)
set(BUILD_CINEMA OFF CACHE BOOL "" FORCE)
set(BUILD_ROM_TEST OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
set(BUILD_PYTHON OFF CACHE BOOL "" FORCE)
set(BUILD_GL OFF CACHE BOOL "" FORCE)
set(BUILD_GLES2 OFF CACHE BOOL "" FORCE)
set(BUILD_GLES3 OFF CACHE BOOL "" FORCE)
set(USE_DEBUGGERS OFF CACHE BOOL "" FORCE)
set(USE_GDB_STUB OFF CACHE BOOL "" FORCE)
set(USE_EDITLINE OFF CACHE BOOL "" FORCE)
set(USE_FFMPEG OFF CACHE BOOL "" FORCE)
set(USE_ZLIB OFF CACHE BOOL "" FORCE)
set(USE_MINIZIP OFF CACHE BOOL "" FORCE)
set(USE_PNG OFF CACHE BOOL "" FORCE)
set(USE_LIBZIP OFF CACHE BOOL "" FORCE)
set(USE_SQLITE3 OFF CACHE BOOL "" FORCE)
set(USE_ELF OFF CACHE BOOL "" FORCE)
set(USE_LUA OFF CACHE BOOL "" FORCE)
set(USE_LZMA OFF CACHE BOOL "" FORCE)
set(USE_DISCORD_RPC OFF CACHE BOOL "" FORCE)
set(ENABLE_SCRIPTING OFF CACHE BOOL "" FORCE)

add_subdirectory(
    "${MGBA_UPSTREAM_DIR}"
    "${CMAKE_CURRENT_BINARY_DIR}/mgba"
    EXCLUDE_FROM_ALL
)

add_library(
    linkroom_native
    SHARED
    native_emulator_bridge.cpp
    placeholder_renderer.cpp
    "${MGBA_UPSTREAM_DIR}/src/gba/sio/lockstep.c"
    linkroom_core/emulator_session.cpp
    linkroom_core/link/local_link_session.cpp
    linkroom_core/link/linked_emulator_slot.cpp
    linkroom_core/link/link_scheduler.cpp
    linkroom_core/mgba_core_adapter.cpp
    linkroom_core/save_paths.cpp
    linkroom_core/video_frame_buffer.cpp
)

target_include_directories(
    linkroom_native
    PRIVATE
    linkroom_core
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    linkroom_native
    linkroom_mgba
    ${log-lib}
    ${android-lib}
)
