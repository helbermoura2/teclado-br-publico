#!/usr/bin/env bash
set -euo pipefail
# Run after cleverkeys-port/prepare.py. Requires Android SDK/NDK 28,
# CMake/Ninja, and pinned llama.cpp under hbx-native/llama.cpp.
target="$(cd "${1:?Usage: build-native.sh /path/to/CleverKeys}" && pwd)"
: "${ANDROID_HOME:?Export ANDROID_HOME pointing to Android SDK}"
ndk="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.0.13004108}"
if [ ! -f "$ndk/build/cmake/android.toolchain.cmake" ]; then
  echo "Android NDK 28.0.13004108 was not found." >&2; exit 2
fi
if [ ! -d "$target/hbx-native/llama.cpp/.git" ]; then
  echo "Clone ggml-org/llama.cpp at 0ec4027a2a0183de87c007069adddc06e665f7de into hbx-native/llama.cpp" >&2
  exit 2
fi
have="$(git -C "$target/hbx-native/llama.cpp" rev-parse HEAD)"
if [ "$have" != "0ec4027a2a0183de87c007069adddc06e665f7de" ]; then
  echo "Unexpected llama.cpp commit $have" >&2; exit 2
fi
for abi in ${HBX_ABIS:-arm64-v8a x86_64}; do
  cmake -S "$target/hbx-native" -B "$target/hbx-build/$abi" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-26 \
    -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release \
    -DLLAMA_BUILD_NUMBER=12020 -DLLAMA_BUILD_COMMIT=0ec4027a
  cmake --build "$target/hbx-build/$abi" --target tecladoai -j 3
  mkdir -p "$target/src/main/jniLibs/$abi"
  cp "$target/hbx-build/$abi/libtecladoai.so" "$target/src/main/jniLibs/$abi/"
  echo "Prepared libtecladoai.so for $abi"
done
echo "Native HBX offline libraries prepared. This does not sign or publish APKs."
