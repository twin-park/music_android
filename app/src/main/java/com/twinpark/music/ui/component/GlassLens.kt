package com.twinpark.music.ui.component

/** 노브 렌즈(굴절 셰이더). */
internal const val KNOB_LENS_ZOOM = 1.22f

internal const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
half4 main(float2 p) {
    float2 c = size * 0.5;
    float R = min(size.x, size.y) * 0.5;
    float2 d = (p - c) / R;
    float r = length(d);
    float edge = smoothstep(0.45, 1.0, r);
    // 가운데는 확대, 가장자리는 원래 위치로 이어져 휘어 보인다
    float k = 1.0 - 0.34 * (1.0 - r * r);
    float ca = 0.05 * edge;
    float2 q = c + d * k * R;
    half4 g = content.eval(q);
    half cr = content.eval(c + d * (k + ca) * R).r;
    half cb = content.eval(c + d * (k - ca) * R).b;
    return half4(cr, g.g, cb, g.a);
}
"""

/** API 33 이상에서만 굴절 셰이더를 쓴다. 그 미만은 null이라 단순 확대로 대체된다. */
internal fun createLensShader(): android.graphics.RuntimeShader? =
    if (android.os.Build.VERSION.SDK_INT >= 33) android.graphics.RuntimeShader(LENS_AGSL) else null
