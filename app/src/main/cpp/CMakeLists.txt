cmake_minimum_required(VERSION 3.10.2)
project("lamejni")

# 设置 C++ 标准
set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

# 指定输出 so 名称
add_library(lamejni SHARED
        lame_jni.c
)

# 预先编译好的 libmp3lame.so
add_library(mp3lame SHARED IMPORTED)

# 这里 ${ANDROID_ABI} 是 Gradle 自动传入的变量，对应 armeabi-v7a/arm64-v8a 等
set_target_properties(mp3lame PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libmp3lame.so
)

# 引入系统库
find_library(log-lib log)

# 链接库
target_link_libraries(
        lamejni
        mp3lame
        ${log-lib}
)
