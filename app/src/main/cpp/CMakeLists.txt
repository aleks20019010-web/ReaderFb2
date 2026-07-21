cmake_minimum_required(VERSION 3.22.1)

project("llama_jni")

# NDK & Architecture optimizations for ARM64
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -march=armv8.7-a+dotprod+i8mm -ffast-math")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=armv8.7-a+dotprod+i8mm -ffast-math -std=c++17")

# Include directories for JNI and llama.cpp headers
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

# Build llama_jni shared library
add_library(
    llama_jni
    SHARED
    llama-jni.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    llama_jni
    ${log-lib}
)
