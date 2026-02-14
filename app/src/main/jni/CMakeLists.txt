cmake_minimum_required(VERSION 3.4.1)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# MiniJarvis Native Library
add_library(
    minijarvis_jni
    SHARED
    minijarvis_jni.cpp
)

# Find and link required libraries
find_library(
    log-lib
    log
)

target_link_libraries(
    minijarvis_jni
    ${log-lib}
)

# In production, you would also link against llama.cpp:
# add_library(llama SHARED IMPORTED)
# set_target_properties(llama PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libllama.so)
# target_link_libraries(minijarvis_jni llama)