cmake_minimum_required(VERSION 3.22.1)
project(carbgm C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 엔진 소스는 ../ai/engine 을 그대로 쓴다 (복사하지 않음)
set(ENGINE ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../ai/engine)

add_library(carbgm SHARED
    bgm_jni.cpp
    fluid_tsf.cpp          # fluidsynth 대신 TinySoundFont 로 사운드폰트를 연주(안드로이드 전용, 엔진 fluid.h 인터페이스를 채움)
    ${ENGINE}/src/model.cpp ${ENGINE}/src/gen.cpp ${ENGINE}/src/theory.cpp ${ENGINE}/src/song.cpp
    ${ENGINE}/src/compose.cpp ${ENGINE}/src/vary.cpp ${ENGINE}/src/notation.cpp ${ENGINE}/src/dsp.cpp
    ${ENGINE}/src/piano.cpp ${ENGINE}/src/engine.cpp ${ENGINE}/src/util.cpp
    # 지휘자(상황 → 분위기 · 악기 · 주변 소리)와 주변 소리(파도 · 비) — ai 11-13 판부터
    ${ENGINE}/src/conductor.cpp ${ENGINE}/src/json.cpp ${ENGINE}/src/amb.cpp
    ${ENGINE}/src/ma_impl.c)

target_include_directories(carbgm PRIVATE ${ENGINE}/src)
# 디버그 빌드에서도 최적화를 켠다. (-O0 이면 소리가 실시간을 못 따라간다)
target_compile_options(carbgm PRIVATE -O3 -Wno-unused-function -Wno-unused-result)
target_link_libraries(carbgm log android dl m)
