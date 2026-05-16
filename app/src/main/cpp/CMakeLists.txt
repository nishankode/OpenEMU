cmake_minimum_required(VERSION 3.22.1)

project(linkroom_native)

add_library(
    linkroom_native
    SHARED
    native_emulator_bridge.cpp
    placeholder_renderer.cpp
    linkroom_core/emulator_session.cpp
    linkroom_core/mgba_core_adapter.cpp
    linkroom_core/save_paths.cpp
    linkroom_core/video_frame_buffer.cpp
)

target_include_directories(
    linkroom_native
    PRIVATE
    linkroom_core
)

# Future mGBA hook:
# Add the pinned source under third_party/mgba/upstream in a later phase, then
# include/link only the minimum core sources needed by linkroom_core/mgba_core_adapter.
# Do not add third_party/mgba/upstream to this target in Phase 0.2A.

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    linkroom_native
    ${log-lib}
    ${android-lib}
)
