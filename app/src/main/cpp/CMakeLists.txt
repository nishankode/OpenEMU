cmake_minimum_required(VERSION 3.22.1)

project(linkroom_native)

set(MGBA_UPSTREAM_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/mgba/upstream")

add_library(mgba_headers INTERFACE)
target_include_directories(
    mgba_headers
    INTERFACE
    "${MGBA_UPSTREAM_DIR}/include"
)

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

# Phase 0.2B1 mGBA hook:
# The build currently links the mGBA header interface target only. This verifies
# the Android native toolchain can consume pinned mGBA headers without replacing
# the placeholder renderer or attempting real ROM boot.
#
# Future phase:
# Replace or extend mgba_headers with a minimal compiled mGBA core target used
# only by linkroom_core/mgba_core_adapter.

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    linkroom_native
    mgba_headers
    ${log-lib}
    ${android-lib}
)
