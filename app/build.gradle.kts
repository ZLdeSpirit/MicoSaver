plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.devtools.ksp")
    id("stringfog")
}

apply(plugin = "stringfog")
configure<com.github.megatronking.stringfog.plugin.StringFogExtension> {
    implementation = "com.github.megatronking.stringfog.xor.StringFogImpl"
    packageName = "com.github.megatronking.stringfog.app"
    kg = com.github.megatronking.stringfog.plugin.kg.RandomKeyGenerator()
    mode = com.github.megatronking.stringfog.plugin.StringFogMode.bytes
}

android {
    namespace = "com.m.s.micosaver"
    compileSdk = 36

    //TODO 正式包去掉
//    resourcePrefix = "ms"

    defaultConfig {
        applicationId = "com.mico.saver.videokeeper"
        minSdk = 24
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        setProperty("archivesBaseName", "${rootProject.name}-v${versionName}-${versionCode}")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        viewBinding = true
        buildConfig = true
    }
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")

    api("com.github.bumptech.glide:glide:4.14.2")
    annotationProcessor("com.github.bumptech.glide:compiler:4.14.2")
    implementation("jp.wasabeef:glide-transformations:4.3.0")

    implementation("com.tencent:mmkv:1.3.14")
    implementation("com.airbnb.android:lottie:6.6.2")

    //google
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-config")

    implementation("androidx.room:room-runtime:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    implementation("androidx.room:room-paging:2.7.2")
    implementation("androidx.paging:paging-runtime:3.3.5")

    implementation("com.liulishuo.okdownload:okdownload:1.0.7")
    implementation("com.liulishuo.okdownload:sqlite:1.0.7")
    implementation("com.liulishuo.okdownload:okhttp:1.0.7")
    implementation("com.liulishuo.okdownload:ktx:1.0.7")

    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    // admob
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")

    // 买量用户确认
    implementation("com.reyun.solar.engine.oversea:solar-engine-core:1.2.9.7")
    implementation("com.android.installreferrer:installreferrer:2.2")

    implementation("com.facebook.android:facebook-android-sdk:latest.release")

    implementation("com.google.android.gms:play-services-ads:24.6.0")
    implementation("com.google.ads.mediation:applovin:13.4.0.0")
    implementation("com.google.ads.mediation:ironsource:8.11.1.0")
    implementation("com.google.ads.mediation:mintegral:16.9.91.1")
    implementation("com.google.ads.mediation:pangle:7.6.0.2.0")
    implementation("com.unity3d.ads:unity-ads:4.16.1")
    implementation("com.google.ads.mediation:unity:4.16.1.0")

    implementation("com.github.megatronking.stringfog:xor:5.0.0")
}