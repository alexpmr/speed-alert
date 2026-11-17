plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val releaseKeyStore = System.getenv("PT2VHF_SIGNING_STORE_FILE")
android {
    namespace="com.pt2vhf.speedalert"
    compileSdk=35
    defaultConfig {
        applicationId="com.pt2vhf.speedalert"
        minSdk=26
        targetSdk=34
        versionCode=105
        versionName="1.0.5"
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
    implementation("androidx.work:work-runtime-ktx:2.11.2")
}
