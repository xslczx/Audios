#!/bin/bash
set -e

# 修改这里为你的 NDK 路径
NDK=/Users/lijianglong/Library/Android/sdk/ndk/21.4.7075529
TARGET=aarch64-linux-android
HOST=aarch64-linux-android
API=21  # 最低API level，可改成16
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/darwin-x86_64

CC="$TOOLCHAIN/bin/${TARGET}${API}-clang --sysroot=$TOOLCHAIN/sysroot"
CXX="$TOOLCHAIN/bin/${TARGET}${API}-clang++ --sysroot=$TOOLCHAIN/sysroot"

ARCHS=("armeabi-v7a" "arm64-v8a")

for ARCH in "${ARCHS[@]}"; do
  echo "=== Building for $ARCH ==="

  case $ARCH in
    armeabi-v7a)
      TARGET=armv7a-linux-androideabi
      HOST=arm-linux-androideabi
      CC=$TOOLCHAIN/bin/${TARGET}${API}-clang
      ;;
    arm64-v8a)
      TARGET=aarch64-linux-android
      HOST=aarch64-linux-android
      CC=$TOOLCHAIN/bin/${TARGET}${API}-clang
      ;;
    x86)
      TARGET=i686-linux-android
      HOST=i686-linux-android
      CC=$TOOLCHAIN/bin/${TARGET}${API}-clang
      ;;
    x86_64)
      TARGET=x86_64-linux-android
      HOST=x86_64-linux-android
      CC=$TOOLCHAIN/bin/${TARGET}${API}-clang
      ;;
  esac

  PREFIX=$(pwd)/android/$ARCH
  mkdir -p $PREFIX/lib

  make distclean >/dev/null 2>&1 || true

  echo "--- Running configure for $ARCH ---"
  if ! ./configure \
      --host=$HOST \
      --build=$(uname -m)-apple-darwin \
      --enable-shared \
      --disable-static \
      --disable-frontend \
      --disable-decoder \
      CC=$CC \
      CFLAGS="-O3 -fPIC -DANDROID -D__ANDROID__ -fdata-sections -ffunction-sections -Wl,--gc-sections -DMP3LAME_DEFAULT_ALIGN=16384" \
      LDFLAGS="-Wl,--gc-sections" \
      --prefix=$PREFIX; then
    echo "❌ configure failed for $ARCH"
    continue
  fi

  echo "--- Building for $ARCH ---"
  if ! make -j$(sysctl -n hw.ncpu); then
    echo "❌ make failed for $ARCH"
    continue
  fi

  if ! make install; then
    echo "❌ make install failed for $ARCH"
    continue
  fi

  if [ -f libmp3lame/.libs/libmp3lame.so ]; then
    cp libmp3lame/.libs/libmp3lame.so $PREFIX/lib/
    echo "✅ libmp3lame.so copied to $PREFIX/lib/"
  else
    echo "❌ libmp3lame.so not found for $ARCH"
  fi
done