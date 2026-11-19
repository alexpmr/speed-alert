plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val releaseKeyStore = System.getenv("PT2VHF_SIGNING_STORE_FILE")
android {
    namespace="com.pt2vhf.speedalert"
    compileSdk=36
    defaultConfig {
        applicationId="com.pt2vhf.speedalert"
        minSdk=26
        targetSdk=36
        versionCode=109
        versionName="1.0.9"
    }
    buildFeatures { buildConfig=true }
    // github: APK distributed by pt2vhf.com/GitHub, with the site OTA updater and opt-in stats.
    // play:   AAB for Google Play. Play policy forbids self-updates outside Play, so the
    //         OTA updater, REQUEST_INSTALL_PACKAGES and network stats are compiled out.
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension="store"
            buildConfigField("boolean","OTA","true")
            buildConfigField("boolean","ONLINE_TILES","false")
        }
        create("play") {
            dimension="store"
            buildConfigField("boolean","OTA","false")
            // Outside the prebuilt tiles, fetch ~11 km cells from OpenStreetMap (worldwide).
            buildConfigField("boolean","ONLINE_TILES","true")
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
    testImplementation("junit:junit:4.13.2")
}

// The site APK bundles the DF database (not in Git) so it works offline from the first run;
// a hand-built release without it shows "BASE OFFLINE INDISPONÍVEL". Other areas come as tiles.
//   bash scripts/restore_osm_db.sh app/src/github/assets/df_roads_v3.sqlite
val requireGithubDatabase by tasks.registering {
    doLast {
        val f = file("src/github/assets/df_roads_v3.sqlite")
        if (!f.isFile || f.length() < 10_000_000) throw GradleException(
            "Base offline do DF ausente. Rode antes: bash scripts/restore_osm_db.sh app/src/github/assets/df_roads_v3.sqlite")
    }
}
tasks.matching { it.name == "assembleGithubRelease" || it.name == "packageGithubRelease" }
    .configureEach { dependsOn(requireGithubDatabase) }
