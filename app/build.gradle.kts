plugins {
    id("com.android.application")
}

val vCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val vName = (findProperty("versionName") as String?) ?: "1.0"

android {
    namespace = "com.brawldns.switcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.brawldns.switcher"
        minSdk = 29
        targetSdk = 34
        versionCode = vCode
        versionName = vName
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("brawldns.keystore")
            storeType = "pkcs12"
            storePassword = "brawldns"
            keyAlias = "brawldns"
            keyPassword = "brawldns"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
