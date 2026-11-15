package com.twinpark.music.domain

import org.json.JSONObject

/** Gleo 음성 비서가 앱 함수를 부를 때 쓰는 값. `assets/schema.json`의 enum과 같아야 한다(테스트가 확인). */
enum class GleoMood(val wire: String, val engineId: String) {
    RainyDay("rainy_day", "비오는날"),
    CalmJazz("calm_jazz", "평온한재즈"),
    NightDrive("night_drive", "밤드라이브"),
    CheerfulConversation("cheerful_conversation", "즐거운대화"),
    SeasideBossaNova("seaside_bossa_nova", "바닷가보사노바"),
    Lullaby("lullaby", "아이재우기"),
    Soothing("soothing", "진정"),
    Comforting("comforting", "위로"),
    DayDrive("day_drive", "낮드라이브"),
    ClassicalPiano("classical_piano", "클래식피아노"),
    StringEnsemble("string_ensemble", "현악앙상블"),
    ClassicalWaltz("classical_waltz", "클래식왈츠"),
    // ai 11-13 판에서 늘어난 분위기 20개
    FreshMorning("fresh_morning", "상쾌한아침"),
    NightHighway("night_highway", "밤고속도로"),
    LofiCafe("lofi_cafe", "로파이"),
    BluesShuffle("blues_shuffle", "블루스셔플"),
    BigBandSwing("big_band_swing", "빅밴드"),
    CityPopDrive("city_pop_drive", "시티팝"),
    FunkyGroove("funky_groove", "펑키그루브"),
    EightiesNeon("eighties_neon", "80년대"),
    SambaFestival("samba_festival", "삼바"),
    ReggaeBeach("reggae_beach", "레게"),
    TropicalIsland("tropical_island", "트로피컬"),
    TangoNight("tango_night", "탱고"),
    CelticFields("celtic_fields", "켈틱"),
    CountryRoad("country_road", "컨트리"),
    StarlightAmbient("starlight_ambient", "별빛앰비언트"),
    KidsSingalong("kids_singalong", "동요"),
    AcousticCamping("acoustic_camping", "캠핑포크"),
    SnowyEvening("snowy_evening", "겨울저녁"),
    BaroqueStroll("baroque_stroll", "바로크"),
    EpicCinematic("epic_cinematic", "웅장한영화");

    companion object {
        fun fromWire(wire: String?): GleoMood? = entries.firstOrNull { it.wire == wire }
    }
}

sealed interface GleoCommand {
    data class SetMood(val mood: GleoMood) : GleoCommand
    data class SetSleep(val enabled: Boolean) : GleoCommand

    /** 필요한 값이 없거나 허용되지 않는 값이라 더 물어봐야 한다. */
    data class NeedsInput(val functionName: String, val field: String) : GleoCommand

    data class Unsupported(val functionName: String?) : GleoCommand
}

object GleoCommands {
    const val SET_MUSIC_MOOD = "SET_MUSIC_MOOD"
    const val SET_SLEEP_MODE = "SET_SLEEP_MODE"

    /** [action]은 인텐트 action(함수 이름), [params]는 `ACTION_PARAM` 문자열(JSON). */
    fun parse(action: String?, params: String?): GleoCommand {
        val json = runCatching { JSONObject(params.orEmpty().ifBlank { "{}" }) }.getOrNull() ?: JSONObject()
        return when (action) {
            SET_MUSIC_MOOD -> GleoMood.fromWire(json.optString("mood", null))?.let(GleoCommand::SetMood)
                ?: GleoCommand.NeedsInput(SET_MUSIC_MOOD, "mood")
            SET_SLEEP_MODE -> booleanOrNull(json, "enabled")?.let(GleoCommand::SetSleep)
                ?: GleoCommand.NeedsInput(SET_SLEEP_MODE, "enabled")
            else -> GleoCommand.Unsupported(action)
        }
    }

    private fun booleanOrNull(json: JSONObject, key: String): Boolean? = when (val value = json.opt(key)) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }
}
