plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.paparazzi)
}


fun String.asBaseUrl(): String = trim().let { if (it.endsWith("/")) it else "$it/" }
fun String.requireHttps(): String {
    require(startsWith("https://")) { "API_BASE_URL must use https:// for release builds, got '$this'" }
    return this
}
fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "id.sch.mts241alikhlash.cbt"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.sch.mts241alikhlash.cbt"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "BASE_URL",
            "http://10.0.2.2:4100/".asBuildConfigString(),
        )

    }

    buildTypes {
        debug {
            val debugUrl = providers.gradleProperty("API_BASE_URL_DEBUG")
                .orElse(providers.gradleProperty("API_BASE_URL"))
                .orElse("http://10.0.2.2:4100/")
                .get()
                .asBaseUrl()
            buildConfigField("String", "BASE_URL", debugUrl.asBuildConfigString())
        }
        release {
            val releaseUrl = providers.gradleProperty("API_BASE_URL")
                .orElse("https://exam-api.invalid/")
                .get()
                .asBaseUrl()
                .requireHttps()
            buildConfigField("String", "BASE_URL", releaseUrl.asBuildConfigString())
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Paparazzi and Robolectric cannot share a JVM: Paparazzi's layoutlib bridge
        // fails to initialise once Robolectric has run in the same process.
        unitTests.all { it.forkEvery = 1 }
    }

    sourceSets {
        // Robolectric unit tests read the exported Room schemas from the merged assets.
        // Only debug and androidTest carry them: the release APK must not ship test
        // fixtures, so `ExamDatabaseMigrationTest` skips itself when they are absent
        // (i.e. under `testReleaseUnitTest`).
        getByName("debug").assets.srcDir("$projectDir/schemas")
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
