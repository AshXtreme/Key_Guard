cmake_minimum_required(VERSION 3.22.1)

project("keyguard_audio" CXX)

# Enforce standard C++20
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Discover Google Oboe exposed via Prefab
find_package(oboe REQUIRED CONFIG)

# Native audio engine shared library
add_library(keyguard_audio SHARED
    keyguard_audio.cpp
)

# Link oboe prefab target along with Android system libraries
target_link_libraries(keyguard_audio
    PRIVATE
    oboe::oboe
    android
    log
)
