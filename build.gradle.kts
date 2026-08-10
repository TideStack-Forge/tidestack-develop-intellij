plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.16.0"
}

group = "com.ouroboros"
version = "0.1.0"

val metadataRuntimeDir = file("common/amis-schema/runtime")
val metadataEditorCommonDir = file("common/amis-schema")
val metadataHostRuntime = file("common/amis-schema/dist/browser/metadata-editor-host.js")
val metadataContributionManifest = file("common/amis-schema/src/metadataTypeContributions.json")
val tideStackDevelopConfigContract = file("common/contracts/tidestack-develop-config.json")
val tideStackI18nDir = file("common/amis-schema/src/i18n")
val amisSdkDir = file("common/amis-sdk")
val requiredMetadataEditorAssets = listOf(
    metadataRuntimeDir.resolve("metadata-editor-runtime.js"),
    metadataRuntimeDir.resolve("metadata-editor-webview.html"),
    metadataRuntimeDir.resolve("metadata-editor-amis-editor.js"),
    metadataRuntimeDir.resolve("metadata-editor-amis-editor.css"),
    metadataHostRuntime,
    metadataContributionManifest,
    tideStackDevelopConfigContract,
    tideStackI18nDir.resolve("zh-CN.json"),
    tideStackI18nDir.resolve("en-US.json"),
    amisSdkDir.resolve("sdk.js"),
    amisSdkDir.resolve("sdk.css"),
    amisSdkDir.resolve("cxd.css"),
    amisSdkDir.resolve("dark.css"),
    amisSdkDir.resolve("thirds/@fortawesome/fontawesome-free/webfonts/fa-solid-900.woff2")
)

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
}

intellij {
    version.set("2023.1")
    type.set("IC") // IntelliJ IDEA Community Edition
    plugins.set(listOf("java", "maven", "gradle"))
    sameSinceUntilBuild.set(false)
    updateSinceUntilBuild.set(false)
}

tasks {
    val buildMetadataEditorCommon by registering(Exec::class) {
        workingDir(metadataEditorCommonDir)
        commandLine("npm", "run", "build")
        inputs.files(fileTree(metadataEditorCommonDir) {
            include("src/**", "package.json", "tsconfig*.json", "vite.browser-host.config.ts")
        })
        outputs.file(metadataHostRuntime)
    }

    val verifyMetadataEditorAssets by registering {
        inputs.files(requiredMetadataEditorAssets)
        doLast {
            val cssAssetPattern = Regex("url\\([\"']?([^\"')]+)[\"']?\\)")
            val cssReferencedAssets = listOf("sdk.css", "cxd.css", "dark.css").flatMap { cssName ->
                val cssFile = amisSdkDir.resolve(cssName)
                if (!cssFile.isFile) {
                    emptyList()
                } else {
                    cssAssetPattern.findAll(cssFile.readText())
                        .map { it.groupValues[1] }
                        .filterNot { it.startsWith("data:") || it.startsWith("http:") || it.startsWith("https:") }
                        .map { amisSdkDir.resolve(it.removePrefix("./")) }
                        .toList()
                }
            }
            val missing = (requiredMetadataEditorAssets + cssReferencedAssets).distinct().filterNot { it.isFile }
            if (missing.isNotEmpty()) {
                error("Missing metadata editor assets:\n${missing.joinToString("\n") { "- ${it.absolutePath}" }}")
            }
        }
    }

    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    withType<Test> {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set("231")
    }

    processResources {
        dependsOn(verifyMetadataEditorAssets)
        from(metadataRuntimeDir) {
            into("ouroboros-metadata-editor/runtime")
        }
        from(metadataHostRuntime) {
            into("ouroboros-metadata-editor/runtime")
        }
        from(metadataContributionManifest) {
            into("ouroboros-metadata-editor")
        }
        from(tideStackDevelopConfigContract) {
            into("ouroboros-ide-contracts")
        }
        from(tideStackI18nDir) {
            into("tidestack-develop-i18n")
        }
        from(amisSdkDir) {
            into("ouroboros-metadata-editor/amis-sdk")
        }
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
