#!/usr/bin/env bash
set -euo pipefail
source_root="$(cd "$1" && pwd)"
extra=()
if command -v ccache >/dev/null; then extra=(-DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache); fi
for abi in arm64-v8a x86_64; do
  cmake -S "$source_root/ai-native" -B "$source_root/ai-build/$abi" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_HOME/ndk/28.0.13004108/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release -DLLAMA_BUILD_NUMBER=12020 -DLLAMA_BUILD_COMMIT=0ec4027a "${extra[@]}"
  cmake --build "$source_root/ai-build/$abi" --target tecladoai -j 3
  mkdir -p "$source_root/app/src/main/jniLibs/$abi"
  cp "$source_root/ai-build/$abi/libtecladoai.so" "$source_root/app/src/main/jniLibs/$abi/"
done

