import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val poseLiteUrl =
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
val poseLiteSha256 = "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a"
val poseFullUrl =
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task"
val poseFullSha256 = "5134a3aad27a58b93da0088d431f366da362b44e3ccfbe3462b3827a839011b1"
val poseCacheDir = layout.buildDirectory.dir("pose-models")
val poseAssetDir = layout.buildDirectory.dir("generated/poseAssets")

android {
    namespace = "com.stb.bodymasks"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.stb.bodymasks"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
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

    androidResources {
        noCompress += listOf("task", "tflite")
    }

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
    inputs.property("liteUrl", poseLiteUrl)
    inputs.property("liteSha256", poseLiteSha256)
    inputs.property("fullUrl", poseFullUrl)
    inputs.property("fullSha256", poseFullSha256)
    outputs.dir(poseAssetDir)
    doLast {
        val cache = poseCacheDir.get().asFile
        val assets = poseAssetDir.get().asFile
        cache.mkdirs()
        assets.mkdirs()

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

        fun fetch(url: String, dest: File, expected: String) {
            if (dest.isFile && sha256(dest) == expected) return
            var lastError: Exception? = null
            for (attempt in 1..3) {
                val tmp = File(dest.parentFile, dest.name + ".partial")
                try {
                    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
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
                    if (got != expected) {
                        tmp.delete()
                        error("SHA-256 de ${dest.name} no coincide: $got")
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
                throw lastError ?: IllegalStateException("No se pudo descargar ${dest.name}")
            }
        }

        val models = listOf(
            Triple(poseLiteUrl, cache.resolve("pose_landmarker_lite.task"), poseLiteSha256),
            Triple(poseFullUrl, cache.resolve("pose_landmarker_full.task"), poseFullSha256),
        )
        val aligner = rootProject.file("tools/align_task_bundle.py")
        for ((url, source, sha) in models) {
            fetch(url, source, sha)
            project.exec {
                commandLine(
                    "python3",
                    aligner.absolutePath,
                    "--input",
                    source.absolutePath,
                    "--output",
                    assets.resolve(source.name).absolutePath,
                )
            }
        }
    }
}

tasks.named("preBuild").configure { dependsOn(downloadPoseModel) }
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(downloadPoseModel)
    }
}
