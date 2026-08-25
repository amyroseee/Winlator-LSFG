#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="${1:-/tmp/lsfg-vk-glibc-source}"
BUILD_DIR="${2:-/tmp/lsfg-vk-glibc-build}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ ! -d "$SOURCE_DIR/.git" ]]; then
    git clone --branch v2.0.0-dev --depth 1 \
        https://github.com/PancakeTAS/lsfg-vk.git "$SOURCE_DIR"
fi

git -C "$SOURCE_DIR" checkout --detach 8b0da2661c6f3473a7fccc8ba643880050e71642
if git -C "$SOURCE_DIR" apply --reverse --check "$SCRIPT_DIR/compatibility.patch"; then
    echo "Compatibility patch already applied"
else
    git -C "$SOURCE_DIR" apply --check "$SCRIPT_DIR/compatibility.patch"
    git -C "$SOURCE_DIR" apply "$SCRIPT_DIR/compatibility.patch"
fi

cmake -S "$SOURCE_DIR" -B "$BUILD_DIR" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_SYSTEM_NAME=Linux \
    -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
    -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++ \
    -DCMAKE_CXX_FLAGS=-I/usr/include \
    -DLSFGVK_BUILD_VK_LAYER=ON \
    -DLSFGVK_BUILD_CLI=OFF \
    -DLSFGVK_BUILD_UI=OFF \
    -DLSFGVK_LAYER_LIBRARY_PATH=liblsfg-vk.so

cmake --build "$BUILD_DIR" --parallel

echo "Built: $BUILD_DIR/lsfg-vk-layer/liblsfg-vk-layer.so"
