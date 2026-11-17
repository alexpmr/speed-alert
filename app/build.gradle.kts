plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace="com.pt2vhf.speedalert"
    compileSdk=35
    defaultConfig {
        applicationId="com.pt2vhf.speedalert"
        minSdk=26
        targetSdk=34
        versionCode=5
        versionName="1.0.4-beta.1"
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
