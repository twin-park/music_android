#!/bin/bash
# 폰(arm64)에서 엔진을 시험하기 위한 engine_cli 빌드: 사운드폰트는 fluidsynth 대신 TinySoundFont(app/src/main/cpp/fluid_tsf.cpp).
#   NDK=<ndk 폴더>/toolchains/llvm/prebuilt/<host>/bin  tools/build_engine_cli.sh   -> tools/build/engine_cli
# 폰에서 실행: adb push 해서 `./engine_cli render data out.wav 60 "0:즐거운대화"`
#   (data 에 chord.bin melody.bin tune.txt sf/ piano/ 와 app/bgm/calib_tsf.txt 가 있어야 하고, CAR_BGM_CALIB=calib_tsf.txt 를 준다)
# 보정표를 다시 만들 때: CAR_BGM_CALIB=calib_tsf.txt ./engine_cli calib data 75   (소리 크기가 fluidsynth 와 달라서 따로 만든다)
set -e
cd "$(dirname "$0")"
: "${NDK:?NDK 환경변수에 clang 이 있는 bin 폴더를 지정하세요}"
ENGINE=../../ai/engine
mkdir -p build
SRC="$ENGINE/src/model.cpp $ENGINE/src/gen.cpp $ENGINE/src/theory.cpp $ENGINE/src/song.cpp $ENGINE/src/compose.cpp $ENGINE/src/vary.cpp $ENGINE/src/notation.cpp $ENGINE/src/dsp.cpp $ENGINE/src/piano.cpp ../app/src/main/cpp/fluid_tsf.cpp $ENGINE/src/engine.cpp $ENGINE/src/util.cpp"
"$NDK/clang++" --target=aarch64-linux-android29 -O3 -std=c++17 -static-libstdc++ -I"$ENGINE/src" -Wall -Wno-unused-function -Wno-unused-result -pthread   -o build/engine_cli "$ENGINE/tools/engine_cli.cpp" $SRC
echo "-> tools/build/engine_cli"
