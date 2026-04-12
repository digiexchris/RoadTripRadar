plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ca.voiditswarranty.roadtripradar"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ca.voiditswarranty.roadtripradar"
        minSdk = 26
        targetSdk = 36
        versionCode = 25
        versionName = "1.10.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "RoadTripRadar Dev")
        }
        release {
            resValue("string", "app_name", "RoadTripRadar")
            isMinifyEnabled = false
            vcsInfo.include = false
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
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

}

val syncMakiIcons by tasks.registering(Sync::class) {
    from("${rootProject.projectDir}/libs/maki/icons")
    into("${projectDir}/src/main/assets/maki")
    include("*.svg")
}

abstract class CheckMakiIconsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val makiDir: DirectoryProperty

    @TaskAction
    fun check() {
        val dir = makiDir.get().asFile
        val svgs = dir.listFiles()?.filter { it.extension == "svg" } ?: emptyList()
        if (svgs.isEmpty()) {
            throw GradleException(
                "Maki icon SVGs not found in ${dir.absolutePath}. " +
                "Run 'git submodule update --init' to fetch the maki icons."
            )
        }
        logger.lifecycle("checkMakiIcons: found ${svgs.size} SVG icons")
    }
}

val checkMakiIcons by tasks.registering(CheckMakiIconsTask::class) {
    dependsOn(syncMakiIcons)
    makiDir.set(layout.projectDirectory.dir("src/main/assets/maki"))
}

tasks.named("preBuild") { dependsOn(checkMakiIcons) }

// Ensure reproducible APK builds (deterministic ZIP ordering and no timestamps)
tasks.withType<Zip>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

// Disable baseline profile generation for reproducible builds
tasks.whenTaskAdded {
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.maplibre.compose)
    implementation(libs.maplibre.compose.material3)
    implementation(libs.spatialk.turf)
    implementation(libs.androidsvg)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}