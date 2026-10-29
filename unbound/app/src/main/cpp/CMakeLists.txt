cmake_minimum_required(VERSION 3.22.1)
project(unbound C)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# mGBA as a static library: only the GBA core, without frontends or optional dependencies
set(LIBMGBA_ONLY ON CACHE BOOL "" FORCE)
set(M_CORE_GBA ON CACHE BOOL "" FORCE)
set(M_CORE_GB OFF CACHE BOOL "" FORCE)
set(USE_DEBUGGERS OFF CACHE BOOL "" FORCE)
set(ENABLE_SCRIPTING OFF CACHE BOOL "" FORCE)
add_subdirectory(mgba EXCLUDE_FROM_ALL)

add_library(unbound SHARED unbound_jni.c)
target_include_directories(unbound PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/mgba/include
    ${CMAKE_CURRENT_BINARY_DIR}/mgba/include
)
target_compile_options(unbound PRIVATE -O2 -Wall)
target_compile_options(mgba PRIVATE -O3)

if(ANDROID)
    target_link_libraries(unbound mgba log)
else()
    # Host build, used to check the bridge compiles
    find_package(JNI REQUIRED)
    target_include_directories(unbound PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(unbound mgba m)
endif()
