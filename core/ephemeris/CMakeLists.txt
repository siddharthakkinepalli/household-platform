cmake_minimum_required(VERSION 3.22.1)
project("sweph_bridge" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Explicitly list Swiss Ephemeris source files to ensure they are compiled
set(SWE_SOURCES
    src/main/cpp/swe/swedate.c
    src/main/cpp/swe/swehouse.c
    src/main/cpp/swe/swejpl.c
    src/main/cpp/swe/swemmoon.c
    src/main/cpp/swe/swemplan.c
    src/main/cpp/swe/sweph.c
    src/main/cpp/swe/swephlib.c
    src/main/cpp/swe/swecl.c
    src/main/cpp/swe/swehel.c
)

add_library(
    sweph_bridge
    SHARED
    src/main/cpp/sweph_bridge.cpp
    ${SWE_SOURCES}
)

# Expose swe/ headers to the bridge
target_include_directories(sweph_bridge PRIVATE
    src/main/cpp/swe
)

# Strip debug symbols in release to reduce .so size
target_compile_options(sweph_bridge PRIVATE
    $<$<CONFIG:Release>:-O2 -fvisibility=hidden>
)

# Link Android log for JNI error reporting (no PII — codes only)
find_library(log-lib log)
target_link_libraries(sweph_bridge ${log-lib})
