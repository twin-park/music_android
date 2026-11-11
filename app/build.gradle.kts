plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val bgmAssetsDir = layout.buildDirectory.dir("generated/bgmAssets")

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

        // 차량 BGM 엔진(C++)은 arm64 기기만 지원한다
        ndk { abiFilters += "arm64-v8a" }
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
    }

    // 엔진 데이터(AI 모델, 사운드폰트, 피아노 약 130MB)를 에셋 `bgm/`으로 묶는다. 이미 압축된 형식이라 다시 압축하지 않는다.
    sourceSets.getByName("main").assets.srcDir(bgmAssetsDir)
    androidResources { noCompress += listOf("bin", "sf2", "flac", "txt") }
}

// ai/engine/data 에서 엔진이 읽는 파일만 골라 `build/generated/bgmAssets/bgm/`에 모은다. 바뀌지 않으면 건너뛴다.
// 사운드폰트와 피아노는 저장소에 없다(ai/README.md, docs/bgm-engine.md 참고). 없으면 소리 없는 앱이 만들어지므로 경고한다.
val bgmDataSrc = rootProject.layout.projectDirectory.dir("../ai/engine/data")
val copyBgmData by tasks.registering(Sync::class) {
    from(bgmDataSrc) { include("chord.bin", "melody.bin", "tune.txt", "sf/**", "piano/**") }
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
