import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val poseModelUrl =
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
val poseModelSha256 = "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a"
val poseAssetDir = layout.buildDirectory.dir("generated/poseAssets")

android {
    namespace = "com.stb.bodymasks"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.stb.bodymasks"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // El decodificador solo carga userspace de 32 bits. Cualquier .so arm64 queda fuera del APK.
        ndk {
            abiFilters += "armeabi-v7a"
        }
    }

    signingConfigs {
        create("stbDebug") {
            // Clave de depuración pública y estable para que `adb install -r` sustituya la app.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stbDebug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stbDebug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        viewBinding = true
        buildConfig = false
    }

    sourceSets.getByName("main").assets.srcDir(poseAssetDir)

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/INDEX.LIST",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")

    val cameraX = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // 1.2.3: el .so armeabi-v7a vive en tasks-core (libmediapipe_tasks_jni.so) e incluye Pose Landmarker.
    implementation("com.google.mediapipe:tasks-vision:1.2.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
}

val downloadPoseModel = tasks.register("downloadPoseModel") {
    inputs.property("url", poseModelUrl)
    inputs.property("sha256", poseModelSha256)
    outputs.dir(poseAssetDir)
    doLast {
        val dir = poseAssetDir.get().asFile
        dir.mkdirs()
        val dest = dir.resolve("pose_landmarker_lite.task")

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        if (dest.isFile && sha256(dest) == poseModelSha256) {
            return@doLast
        }

        var lastError: Exception? = null
        for (attempt in 1..3) {
            val tmp = dir.resolve("pose_landmarker_lite.task.partial")
            try {
                val connection = (URI(poseModelUrl).toURL().openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    setRequestProperty("User-Agent", "stb-body-masks-gradle")
                }
                connection.inputStream.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                connection.disconnect()
                val got = sha256(tmp)
                if (got != poseModelSha256) {
                    tmp.delete()
                    error("SHA-256 del modelo de pose no coincide: $got")
                }
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                lastError = null
                break
            } catch (error: Exception) {
                tmp.delete()
                lastError = error
                Thread.sleep(1500L * attempt)
            }
        }
        if (lastError != null || !dest.isFile) {
            throw lastError ?: IllegalStateException("No se pudo descargar el modelo de pose")
        }
    }
}

tasks.named("preBuild").configure { dependsOn(downloadPoseModel) }
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(downloadPoseModel)
    }
}
