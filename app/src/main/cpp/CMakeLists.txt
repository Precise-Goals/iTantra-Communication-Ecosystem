cmake_minimum_required(VERSION 3.22.1)

project("itantra_native")

# Adds the native-lib.cpp source file to this target
add_library(
    itantra_native
    SHARED
    native-lib.cpp
)

# Searches for a specified prebuilt library and stores the path as a variable
find_library(
    log-lib
    log
)

# Specifies libraries CMake should link to your target library
target_link_libraries(
    itantra_native
    ${log-lib}
)
