plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val releaseKeyStore = System.getenv("PT2VHF_SIGNING_STORE_FILE")
android {
    namespace="com.pt2vhf.speedalert"
    compileSdk=36
    defaultConfig {
        applicationId="com.pt2vhf.speedalert"
        minSdk=26
        targetSdk=36
        versionCode=108
        versionName="1.0.8"
    }
    buildFeatures { buildConfig=true }
    // Regional OSM databases for Google Play (Play Asset Delivery). Only AABs carry asset packs:
    // the github APK keeps the DF database in src/github/assets instead.
    assetPacks += listOf(":osm_centro_oeste", ":osm_sul", ":osm_sudeste")
    // github: APK distributed by pt2vhf.com/GitHub, with the site OTA updater and opt-in stats.
    // play:   AAB for Google Play. Play policy forbids self-updates outside Play, so the
    //         OTA updater, REQUEST_INSTALL_PACKAGES and network stats are compiled out.
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension="store"
            buildConfigField("boolean","OTA","true")
        }
        create("play") {
            dimension="store"
            buildConfigField("boolean","OTA","false")
        }
    }
    signingConfigs {
        if (!releaseKeyStore.isNullOrBlank()) {
            create("release") {
                storeFile=file(releaseKeyStore)
                storePassword=System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias=System.getenv("ANDROID_KEY_ALIAS")
                keyPassword=System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled=false
            if (!releaseKeyStore.isNullOrBlank()) {
                signingConfig=signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_17
        targetCompatibility=JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget="17" }
}
dependencies {
    implementation("androidx.core:core:1.15.0")
    "githubImplementation"("androidx.work:work-runtime-ktx:2.11.2")
    "playImplementation"("com.google.android.play:app-update:2.1.0")
    "playImplementation"("com.google.android.play:asset-delivery:2.3.0")
}

// A release without its offline databases installs fine but shows "BASE OFFLINE INDISPONÍVEL"
// (this happened with a hand-built 1.0.6 AAB). The databases are not in Git: restore them first.
//   Play AAB:    bash scripts/restore_osm_regions.sh
//   Site APK:    bash scripts/restore_osm_db.sh app/src/github/assets/df_roads_v3.sqlite
val requirePlayDatabases by tasks.registering {
    doLast {
        val missing = listOf("centro-oeste", "sul", "sudeste").filter { r ->
            val f = rootProject.file("osm_${r.replace('-', '_')}/src/main/assets/osm/$r.sqlite")
            !f.isFile || f.length() < 10_000_000
        }
        if (missing.isNotEmpty()) throw GradleException(
            "Bases offline ausentes para: $missing. Rode antes: bash scripts/restore_osm_regions.sh")
    }
}
val requireGithubDatabase by tasks.registering {
    doLast {
        val f = file("src/github/assets/df_roads_v3.sqlite")
        if (!f.isFile || f.length() < 10_000_000) throw GradleException(
            "Base offline do DF ausente. Rode antes: bash scripts/restore_osm_db.sh app/src/github/assets/df_roads_v3.sqlite")
    }
}
tasks.matching { it.name == "bundlePlayRelease" || it.name == "packagePlayReleaseBundle" }
    .configureEach { dependsOn(requirePlayDatabases) }
tasks.matching { it.name == "assembleGithubRelease" || it.name == "packageGithubRelease" }
    .configureEach { dependsOn(requireGithubDatabase) }
