plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "dk.lishan.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dk.lishan.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Lishan-serveren. Adresse og OAuth-klient bliver til konstanter i BuildConfig.
        buildConfigField("String", "LISHAN_BASE_URL", "\"https://lishan.fak.dk\"")
        val clientId = providers.gradleProperty("lishan.clientId").getOrElse("")
        buildConfigField("String", "LISHAN_CLIENT_ID", "\"$clientId\"")
        // AppAuth: browseren sender brugeren tilbage til appen på dk.lishan.app:/oauth2redirect.
        manifestPlaceholders["appAuthRedirectScheme"] = "dk.lishan.app"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // JSON til kommunikation med Lishan-serveren. Kræver serialization-plugin'et øverst.
    implementation(libs.kotlinx.serialization.json)
    // Login med OAuth (browser + PKCE) og HTTP-kald til Lishan-serveren.
    implementation(libs.appauth)
    implementation(libs.okhttp)
    // En falsk HTTP-server til at teste kaldene til Lishan-serveren.
    testImplementation(libs.okhttp.mockwebserver)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    constraints {
        // room-testing kræver mindst 1.8.1, men Room trækker selv 1.7.3 ind i appen; JSON-biblioteket bruger 1.9.0.
        // Testene kører med appens versioner, så appens version skal løftes.
        implementation(libs.kotlinx.serialization.core) {
            because("room-testing (MigrationTestHelper) kræver kotlinx-serialization 1.8.1")
        }
    }
}
// Room gemmer en JSON-beskrivelse af hver databaseversion her. Filerne skal i git:
// de bruges til at teste migrations.
room {
    schemaDirectory("$projectDir/schemas")
}
