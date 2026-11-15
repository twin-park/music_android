import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val bgmAssetsDir = layout.buildDirectory.dir("generated/bgmAssets")

// 날씨 API 키와 위치는 저장소에 올라가지 않는 android/local.properties에서 읽는다(없으면 비워 둬서 날씨 연동을 끈다).
//   DEV_MODE=true   (시연용 DEV 패널과 CHANGE THE MOOD 버튼, 기본 꺼짐. 환경변수 DEV_MODE로도 줄 수 있음)
//   VOICE_COMMAND=true   (음성 명령, Pleos STT 온디바이스. 기본 꺼짐)
//   PLEOS_CLIENT_ID=...   PLEOS_CLIENT_SECRET=...   (Pleos Playground My Project 값. 서버 방식 음성 인식에만 필요, 필수 아님)
//   OPENWEATHER_API_KEY=...   (필수 아님)   WEATHER_LAT=37.5665   WEATHER_LON=126.9780   (필수 아님, 기본 서울)
// 앱에 넣은 키는 APK에서 꺼낼 수 있다. 대회, 시연용이며 정식 배포는 서버를 거쳐야 한다.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(name: String, default: String = ""): String =
    ((findProperty(name) as String?) ?: localProps.getProperty(name) ?: System.getenv(name) ?: default).trim()

android {
    namespace = "com.twinpark.music"
    ndkVersion = "27.1.12297006"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.twinpark.music"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 마이크로 소리를 듣는 기능. 기본 꺼짐이고 마이크 권한은 디버그 빌드에만 있다. 켜려면 -PMIC_SENSING=true
        buildConfigField("boolean", "MIC_SENSING", localProp("MIC_SENSING", "false"))
        // 시연용 DEV 패널과 CHANGE THE MOOD 버튼. 기본 꺼짐. 켜려면 -PDEV_MODE=true, local.properties 또는 환경변수 DEV_MODE=true
        buildConfigField("boolean", "DEV_MODE", localProp("DEV_MODE", "false"))
        // 음성 명령(Pleos STT 온디바이스). 기본 꺼짐. 켜려면 -PVOICE_COMMAND=true, local.properties 또는 환경변수 VOICE_COMMAND=true
        buildConfigField("boolean", "VOICE_COMMAND", localProp("VOICE_COMMAND", "false"))
        // 음성 인식 구현: android(기본, 안드로이드 기본 인식) 또는 pleos(Pleos STT 온디바이스, 플랫폼에서 열리면)
        buildConfigField("String", "VOICE_ENGINE", "\"${localProp("VOICE_ENGINE", "android")}\"")
        // Pleos Playground My Project 의 Client ID / Secret. 서버 방식 음성 인식(registerApp)에만 쓰고, 온디바이스는 필요 없다. 앱에 넣은 값은 APK에서 꺼낼 수 있다.
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${localProp("PLEOS_CLIENT_ID")}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${localProp("PLEOS_CLIENT_SECRET")}\"")
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"${localProp("OPENWEATHER_API_KEY")}\"")
        buildConfigField("double", "WEATHER_LAT", localProp("WEATHER_LAT", "37.5665"))      // 위치는 차량 위치 SDK(FusedLocation)가 준비되면 그쪽 값으로 바꾼다
        buildConfigField("double", "WEATHER_LON", localProp("WEATHER_LON", "126.9780"))

        // 차량 BGM 엔진(C++)은 arm64 기기만 지원한다
        ndk { abiFilters += if (project.hasProperty("emuAbi")) "x86_64" else "arm64-v8a" }   // -PemuAbi: x86_64 에뮬레이터용(arm64 대신)
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_static") } }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 엔진 데이터(AI 모델, 사운드폰트, 피아노 약 130MB)를 에셋 `bgm/`으로 묶는다. 이미 압축된 형식이라 다시 압축하지 않는다.
    sourceSets.getByName("main").assets.srcDir(bgmAssetsDir)
    androidResources { noCompress += listOf("bin", "sf2", "flac", "txt", "onnx") }
}

// ai/engine/data 에서 엔진이 읽는 파일만 골라 `build/generated/bgmAssets/bgm/`에 모은다. 바뀌지 않으면 건너뛴다.
// 사운드폰트와 피아노는 저장소에 없다(ai/README.md, docs/bgm-engine.md 참고). 없으면 소리 없는 앱이 만들어지므로 경고한다.
val bgmDataSrc = rootProject.layout.projectDirectory.dir("../ai/engine/data")
val copyBgmData by tasks.registering(Sync::class) {
    from(bgmDataSrc) { include("chord.bin", "melody.bin", "tune.txt", "sf/**", "piano/**", "conductor/**", "amb/**") }   // conductor: 지휘자 규칙(JSON) · amb: 파도 · 빗소리
    // 악기 크기 보정표는 소리를 내는 쪽(TinySoundFont)에 맞춰 안드로이드 쪽에서 관리한다(ai 에는 없음)
    from(layout.projectDirectory.dir("bgm")) { include("calib_tsf.txt") }
    into(bgmAssetsDir.map { it.dir("bgm") })
    doFirst {
        val dir = bgmDataSrc.asFile
        val missing = listOf("chord.bin", "melody.bin", "tune.txt", "sf/GeneralUser-GS.sf2", "sf/sso_small.sf2", "piano/021_A0v04.flac")
            .filterNot { File(dir, it).exists() }
        if (missing.isNotEmpty()) logger.warn("경고: 엔진 데이터가 없어 앱에 음악이 들어가지 않습니다: $missing (폴더 $dir)")
    }
}
tasks.named("preBuild") { dependsOn(copyBgmData) }

// 차량 API(android.car) 스텁: SDK 플랫폼 36에 들어 있다. 실행할 때는 차량 시스템이 제공하고, 없으면 앱이 제한 없음으로 동작한다.
val carApiStub = androidComponents.sdkComponents.sdkDirectory.map { it.file("platforms/android-36/optional/android.car.jar") }

dependencies {
    compileOnly(files(carApiStub))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")        // 날씨 응답 해석 단위 테스트용(안드로이드의 org.json은 JVM 테스트에서 동작하지 않는다)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
