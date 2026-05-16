# Install script for directory: C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "C:/Program Files (x86)/linkroom_native")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "Debug")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "0")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "TRUE")
endif()

# Set default install directory permissions.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "C:/Users/mnsnn/AppData/Local/Android/Sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-objdump.exe")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/liblinkroom_mgba.a")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/icons/hicolor/16x16/apps" TYPE FILE RENAME "io.mgba.mGBA.png" FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/mgba-16.png")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/icons/hicolor/24x24/apps" TYPE FILE RENAME "io.mgba.mGBA.png" FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/mgba-24.png")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/icons/hicolor/32x32/apps" TYPE FILE RENAME "io.mgba.mGBA.png" FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/mgba-32.png")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/icons/hicolor/48x48/apps" TYPE FILE RENAME "io.mgba.mGBA.png" FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/mgba-48.png")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/icons/hicolor/64x64/apps" TYPE FILE RENAME "io.mgba.mGBA.png" FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/mgba-64.png")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/icons/hicolor/96x96/apps" TYPE FILE RENAME "io.mgba.mGBA.png" FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/mgba-96.png")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/icons/hicolor/128x128/apps" TYPE FILE RENAME "io.mgba.mGBA.png" FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/mgba-128.png")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/icons/hicolor/256x256/apps" TYPE FILE RENAME "io.mgba.mGBA.png" FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/mgba-256.png")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/icons/hicolor/512x512/apps" TYPE FILE RENAME "io.mgba.mGBA.png" FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/mgba-512.png")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgba-devx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include" TYPE DIRECTORY FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/include/mgba" FILES_MATCHING REGEX "/[^/]*\\.h$")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgba-devx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include" TYPE DIRECTORY FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/include/mgba-util" FILES_MATCHING REGEX "/[^/]*\\.h$")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgba-devx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include/mgba" TYPE FILE FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/include/mgba/flags.h")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/doc/mGBA/licenses" TYPE FILE FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/licenses/blip_buf.txt")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/doc/mGBA/licenses" TYPE FILE FILES "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/res/licenses/inih.txt")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/doc/mGBA" TYPE FILE FILES
    "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/README.md"
    "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/README_DE.md"
    "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/README_ES.md"
    "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/README_ZH_CN.md"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xlinkroom_mgbax" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/doc/mGBA" TYPE FILE FILES
    "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/CHANGES"
    "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/LICENSE"
    )
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for each subdirectory.
  include("C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/src/debugger/cmake_install.cmake")
  include("C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/src/feature/cmake_install.cmake")
  include("C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/src/arm/cmake_install.cmake")
  include("C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/src/core/cmake_install.cmake")
  include("C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/src/gb/cmake_install.cmake")
  include("C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/src/gba/cmake_install.cmake")
  include("C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/src/sm83/cmake_install.cmake")
  include("C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/src/util/cmake_install.cmake")
  include("C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/arm64-v8a/mgba/test/cmake_install.cmake")

endif()

