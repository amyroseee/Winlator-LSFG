#!/bin/bash
clear

export ROOTFS="/data/data/com.winlator/files/rootfs"
export CFLAGS="-O2 -Wl,-rpath=$ROOTFS/lib"

rm -r build
mkdir build
cd build
cmake .. -DCMAKE_INSTALL_PREFIX=$ROOTFS/usr -DCMAKE_C_FLAGS_RELEASE="$CFLAGS" -DCMAKE_BUILD_TYPE=Release
make -j8

cd ..

bash create-asset.sh