# This file will be configured to contain variables for CPack. These variables
# should be set in the CMake list file of the project before CPack module is
# included. The list of available CPACK_xxx variables and their associated
# documentation may be obtained using
#  cpack --help-variable-list
#
# Some variables are common to all generators (e.g. CPACK_PACKAGE_NAME)
# and some are specific to a generator
# (e.g. CPACK_NSIS_EXTRA_INSTALL_COMMANDS). The generator specific variables
# usually begin with CPACK_<GENNAME>_xxxx.


set(CPACK_BINARY_DEB "OFF")
set(CPACK_BINARY_FREEBSD "OFF")
set(CPACK_BINARY_IFW "OFF")
set(CPACK_BINARY_NSIS "OFF")
set(CPACK_BINARY_RPM "OFF")
set(CPACK_BINARY_STGZ "ON")
set(CPACK_BINARY_TBZ2 "OFF")
set(CPACK_BINARY_TGZ "ON")
set(CPACK_BINARY_TXZ "OFF")
set(CPACK_BINARY_TZ "ON")
set(CPACK_BUILD_SOURCE_DIRS "C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/src/main/cpp;C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/x86_64")
set(CPACK_CMAKE_GENERATOR "Ninja")
set(CPACK_COMPONENTS_ALL "Unspecified;linkroom_mgba;linkroom_mgba-dev")
set(CPACK_COMPONENT_UNSPECIFIED_HIDDEN "TRUE")
set(CPACK_COMPONENT_UNSPECIFIED_REQUIRED "TRUE")
set(CPACK_DEBIAN_PACKAGE_DEPENDS "libc6")
set(CPACK_DEBIAN_PACKAGE_SECTION "games")
set(CPACK_DEB_COMPONENT_INSTALL "ON")
set(CPACK_DEFAULT_PACKAGE_DESCRIPTION_FILE "C:/Users/mnsnn/AppData/Local/Android/Sdk/cmake/3.22.1/share/cmake-3.22/Templates/CPack.GenericDescription.txt")
set(CPACK_DEFAULT_PACKAGE_DESCRIPTION_SUMMARY "linkroom_native built using CMake")
set(CPACK_GENERATOR "STGZ;TGZ;TZ")
set(CPACK_INSTALL_CMAKE_PROJECTS "C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/x86_64;linkroom_native;ALL;/")
set(CPACK_INSTALL_PREFIX "C:/Program Files (x86)/linkroom_native")
set(CPACK_MODULE_PATH "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/src/platform/cmake/")
set(CPACK_NSIS_DISPLAY_NAME "linkroom_native 0.10-main-15-5c78cc2-dirty")
set(CPACK_NSIS_INSTALLER_ICON_CODE "")
set(CPACK_NSIS_INSTALLER_MUI_ICON_CODE "")
set(CPACK_NSIS_INSTALL_ROOT "$PROGRAMFILES")
set(CPACK_NSIS_PACKAGE_NAME "linkroom_native 0.10-main-15-5c78cc2-dirty")
set(CPACK_NSIS_UNINSTALL_NAME "Uninstall")
set(CPACK_OUTPUT_CONFIG_FILE "C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/x86_64/CPackConfig.cmake")
set(CPACK_PACKAGE_CONTACT "Vicki Pfau <vi@endrift.com>")
set(CPACK_PACKAGE_DEFAULT_LOCATION "/")
set(CPACK_PACKAGE_DESCRIPTION_FILE "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/README.md")
set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "mGBA Game Boy Advance Emulator")
set(CPACK_PACKAGE_FILE_NAME "linkroom_native-0.10-main-15-5c78cc2-dirty-Android")
set(CPACK_PACKAGE_INSTALL_DIRECTORY "linkroom_native 0.10-main-15-5c78cc2-dirty")
set(CPACK_PACKAGE_INSTALL_REGISTRY_KEY "linkroom_native 0.10-main-15-5c78cc2-dirty")
set(CPACK_PACKAGE_NAME "linkroom_native")
set(CPACK_PACKAGE_RELOCATABLE "true")
set(CPACK_PACKAGE_VENDOR "Vicki Pfau")
set(CPACK_PACKAGE_VERSION "0.10-main-15-5c78cc2-dirty")
set(CPACK_PACKAGE_VERSION_MAJOR "0")
set(CPACK_PACKAGE_VERSION_MINOR "10")
set(CPACK_PACKAGE_VERSION_PATCH "5")
set(CPACK_RESOURCE_FILE_LICENSE "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/LICENSE")
set(CPACK_RESOURCE_FILE_README "C:/Users/mnsnn/Documents/SAAS/GBAEmu/third_party/mgba/upstream/README.md")
set(CPACK_RESOURCE_FILE_WELCOME "C:/Users/mnsnn/AppData/Local/Android/Sdk/cmake/3.22.1/share/cmake-3.22/Templates/CPack.GenericWelcome.txt")
set(CPACK_SET_DESTDIR "OFF")
set(CPACK_SOURCE_GENERATOR "TBZ2;TGZ;TXZ;TZ")
set(CPACK_SOURCE_OUTPUT_CONFIG_FILE "C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/x86_64/CPackSourceConfig.cmake")
set(CPACK_SOURCE_RPM "OFF")
set(CPACK_SOURCE_TBZ2 "ON")
set(CPACK_SOURCE_TGZ "ON")
set(CPACK_SOURCE_TXZ "ON")
set(CPACK_SOURCE_TZ "ON")
set(CPACK_SOURCE_ZIP "OFF")
set(CPACK_SYSTEM_NAME "Android")
set(CPACK_THREADS "1")
set(CPACK_TOPLEVEL_TAG "Android")
set(CPACK_WIX_SIZEOF_VOID_P "8")

if(NOT CPACK_PROPERTIES_FILE)
  set(CPACK_PROPERTIES_FILE "C:/Users/mnsnn/Documents/SAAS/GBAEmu/app/.cxx/Debug/1f2w92y4/x86_64/CPackProperties.cmake")
endif()

if(EXISTS ${CPACK_PROPERTIES_FILE})
  include(${CPACK_PROPERTIES_FILE})
endif()

# Configuration for component group "base"

# Configuration for component "linkroom_mgba"

SET(CPACK_COMPONENTS_ALL Unspecified linkroom_mgba linkroom_mgba-dev)
set(CPACK_COMPONENT_LINKROOM_MGBA_GROUP base)

# Configuration for component group "dev"
set(CPACK_COMPONENT_GROUP_DEV_PARENT_GROUP "base")

# Configuration for component "liblinkroom_mgba"

SET(CPACK_COMPONENTS_ALL Unspecified linkroom_mgba linkroom_mgba-dev)
set(CPACK_COMPONENT_LIBLINKROOM_MGBA_GROUP dev)

# Configuration for component "linkroom_mgba-dev"

SET(CPACK_COMPONENTS_ALL Unspecified linkroom_mgba linkroom_mgba-dev)
set(CPACK_COMPONENT_LINKROOM_MGBA-DEV_GROUP dev)

# Configuration for component group "test"
set(CPACK_COMPONENT_GROUP_TEST_PARENT_GROUP "dev")

# Configuration for component "linkroom_mgba-perf"

SET(CPACK_COMPONENTS_ALL Unspecified linkroom_mgba linkroom_mgba-dev)
set(CPACK_COMPONENT_LINKROOM_MGBA-PERF_GROUP test)

# Configuration for component "linkroom_mgba-test"

SET(CPACK_COMPONENTS_ALL Unspecified linkroom_mgba linkroom_mgba-dev)
set(CPACK_COMPONENT_LINKROOM_MGBA-TEST_GROUP test)
