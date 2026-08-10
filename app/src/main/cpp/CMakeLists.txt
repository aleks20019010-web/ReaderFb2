cmake_minimum_required(VERSION 3.22.1)
project("nightread_llama")

add_library(
    nightread_llama
    SHARED
    llama_jni.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    nightread_llama
    ${log-lib}
)
