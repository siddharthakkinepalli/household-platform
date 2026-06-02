cmake_minimum_required(VERSION 3.22.1)
project(llama_bridge)

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/llama.cpp)

# Use llama.cpp's own build system — handles source layout across versions
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory(${LLAMA_DIR} llama_build)

# JNI bridge
add_library(llama_jni SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/llama_bridge.cpp
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
    ${LLAMA_DIR}/common
)

find_library(log-lib log)
target_link_libraries(llama_jni llama ${log-lib} android)
