import com.melta.build.SpritePackTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.melta.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.melta.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Release builds are signed with your own key when these env vars are set (CI secrets),
        // otherwise they fall back to the debug key so the APK still installs for sideloading.
        val keystore = System.getenv("MELTA_KEYSTORE_PATH")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("MELTA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MELTA_KEY_ALIAS")
                keyPassword = System.getenv("MELTA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Every Pokémon sprite (normal + shiny) is gathered at build time and packed into the APK as
// assets/sprites/, so the app works fully offline. Nothing is committed to git.
// Build with -Pmelta.skipSpritePack=true when offline; the app then downloads sprites on demand.
val skipSpritePack = providers.gradleProperty("melta.skipSpritePack").map { it.toBoolean() }.getOrElse(false)

val spritePack = tasks.register<SpritePackTask>("spritePack") {
    group = "melta"
    description = "Downloads and packs all Pokémon sprites into the APK assets."
    maxId.set(1025)
    baseUrl.set("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon")
    variants.set(listOf("", "shiny"))
    cacheDir.set(File(gradle.gradleUserHomeDir, "caches/melta-sprites"))
    outputDir.set(layout.buildDirectory.dir("generated/spritepack"))
}

androidComponents {
    onVariants { variant ->
        if (!skipSpritePack) {
            variant.sources.assets?.addGeneratedSourceDirectory(spritePack, SpritePackTask::outputDir)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
