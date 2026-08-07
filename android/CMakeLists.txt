# Wrapper CMakeLists.txt for react-native-mapsforge-vtm.
#
# The React Native autolinking system includes this as a subdirectory of the
# consuming app's unified CMake build.  It pulls in:
#   1. The codegen-generated Fabric component JNI (generated/jni/).
#   2. The native shared-value bridge (src/main/cpp/) — only when
#      react-native-worklets is available (reanimated's peer dep).
#
# Both benefit from the app-level CMake prefix paths: find_package for
# ReactAndroid, fbjni, and react-native-worklets all resolve correctly here.
cmake_minimum_required(VERSION 3.16)

# Codegen-generated Fabric component + TurboModule JNI (always built).
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/generated/jni/"
                 mapsforgevtm_codegen_build)

# Native shared-value bridge — only when reanimated / worklets is installed.
find_package(react-native-worklets QUIET CONFIG)
if(react-native-worklets_FOUND)
  find_package(ReactAndroid REQUIRED CONFIG)
  add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/"
                   mapsforgevtm_position_writer_build)
  message(STATUS "MapsforgeVtm: native shared-value bridge ENABLED")
else()
  message(STATUS "MapsforgeVtm: react-native-worklets not found — "
          "native shared-value bridge disabled (reanimated not installed)")
endif()
