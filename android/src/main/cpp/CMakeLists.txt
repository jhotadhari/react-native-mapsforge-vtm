# MapsforgeVtm native shared-value bridge.
# Included as a subdirectory from android/CMakeLists.txt ONLY when
# react-native-worklets is available.  The parent build has already:
#   find_package(react-native-worklets REQUIRED CONFIG)
#   find_package(ReactAndroid REQUIRED CONFIG)
# so those targets are available here.

add_library(
  mapsforgevtm_position_writer
  SHARED
  ${CMAKE_CURRENT_SOURCE_DIR}/MapPositionWriter.cpp
)

target_include_directories(
  mapsforgevtm_position_writer
  PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(
  mapsforgevtm_position_writer
  ReactAndroid::jsi
  ReactAndroid::reactnative
  react-native-worklets::worklets
  log
)

# Suppress "unused command line argument" warnings from older NDK compilers
# for flags inherited from worklets' folly-flags.cmake.
target_compile_options(
  mapsforgevtm_position_writer
  PRIVATE
  -Wno-unused-command-line-argument
)
