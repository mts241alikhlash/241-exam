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

// Release signing credentials are read from -P Gradle properties or the matching
// environment variables. No keystore, alias, or password is ever stored in the repo.
// See docs/RELEASING.md.
val releaseSigningProperties = listOf(
    "RELEASE_STORE_FILE",
    "KEYSTORE_PASSWORD",
    "KEY_ALIAS",
    "KEY_PASSWORD",
)
val releaseSigningValues: Map<String, String?> = releaseSigningProperties.associateWith { name ->
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
}
val hasCompleteReleaseSigning = releaseSigningValues.values.all { it != null }
val hasReleaseStoreFile = releaseSigningValues["RELEASE_STORE_FILE"] != null

// Packaging tasks that would emit a release APK/AAB. Unit-test tasks such as
// `testReleaseUnitTest` are intentionally excluded: they must run without credentials.
val releasePackagingRequested = gradle.startParameter.taskNames.any { requested ->
    val task = requested.substringAfterLast(':')
    task == "build" || task == "assemble" || task == "bundle" ||
        listOf("assemble", "bundle", "package", "install", "sign").any { prefix ->
            task.startsWith(prefix) && task.contains("Release")
        }
}

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

    signingConfigs {
        if (hasReleaseStoreFile) {
            create("release") {
                storeFile = file(releaseSigningValues.getValue("RELEASE_STORE_FILE")!!)
                storePassword = releaseSigningValues["KEYSTORE_PASSWORD"]
                keyAlias = releaseSigningValues["KEY_ALIAS"]
                keyPassword = releaseSigningValues["KEY_PASSWORD"]
            }
        }
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
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.findByName("release")
            } else if (releasePackagingRequested) {
                val missing = releaseSigningProperties.filter { releaseSigningValues[it] == null }
                throw GradleException(
                    "Release build needs -PRELEASE_STORE_FILE, -PKEYSTORE_PASSWORD, " +
                        "-PKEY_ALIAS, -PKEY_PASSWORD " +
                        "(or the same names as environment variables). " +
                        "Missing: ${missing.joinToString(", ")}. See docs/RELEASING.md."
                )
            }
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
