import groovy.json.JsonSlurper

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.16.0"
}

group = "com.ouroboros"
version = "0.1.2"

val metadataRuntimeDir = file("common/metadata-editor-runtime/runtime")
val metadataEditorRuntimeDir = file("common/metadata-editor-runtime")
val metadataEditorSchemaDir = file("common/metadata-editor-schema")
val metadataEditorAssetManifest = metadataEditorSchemaDir.resolve("metadataEditorAssets.json")
val metadataEditorAssets = JsonSlurper().parse(metadataEditorAssetManifest) as Map<*, *>
val metadataHostRuntime = file("common/metadata-editor-runtime/dist/browser/metadata-editor-host.js")
val tideStackI18nDir = file("common/metadata-editor-schema/dist/i18n")
val amisSdkDir = file("common/amis-sdk")
fun metadataEditorAssetList(key: String): List<String> {
    return (metadataEditorAssets[key] as? List<*>)?.map { it.toString() } ?: emptyList()
}

val metadataRuntimeAssets = metadataEditorAssetList("runtimeFiles").map(metadataEditorRuntimeDir::resolve)
val metadataSchemaAssets = metadataEditorAssetList("schemaFiles").map { file("common/metadata-editor-schema/dist/$it") }
val tideStackDevelopConfigContracts = metadataEditorAssetList("contractFiles").map { file("common/contracts/$it") }
val tideStackI18nAssets = metadataEditorAssetList("i18nFiles").map(tideStackI18nDir::resolve)
val amisSdkRequiredAssets = metadataEditorAssetList("amisSdkRequiredFiles").map(amisSdkDir::resolve)
val requiredMetadataEditorAssets = listOf(
    metadataHostRuntime,
    amisSdkDir.resolve("thirds/@fortawesome/fontawesome-free/webfonts/fa-solid-900.woff2")
) + metadataRuntimeAssets + metadataSchemaAssets + tideStackDevelopConfigContracts + tideStackI18nAssets + amisSdkRequiredAssets

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
        workingDir(metadataEditorRuntimeDir)
        commandLine("npm", "run", "build")
        inputs.files(fileTree(metadataEditorRuntimeDir) {
            include("src/**", "package.json", "tsconfig*.json", "vite.browser-host.config.ts")
        })
        inputs.files(fileTree(metadataEditorSchemaDir) {
            include("metadata-types/**", "metadata-editors/**", "i18n/**", "tidestackDevelopCapabilities.json", "package.json", "scripts/**")
            include("metadataCreationContract.json", "metadataCreationCases.json", "metadataEditorAssets.json")
        })
        outputs.file(metadataHostRuntime)
        outputs.files(metadataSchemaAssets)
    }

    val verifyMetadataEditorAssets by registering {
        inputs.files(requiredMetadataEditorAssets)
        doLast {
            val cssAssetPattern = Regex("url\\([\"']?([^\"')]+)[\"']?\\)")
            val cssReferencedAssets = metadataEditorAssetList("amisSdkCssFilesWithReferencedAssets").flatMap { cssName ->
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
        metadataSchemaAssets.forEach { asset ->
            from(asset) {
                into("ouroboros-metadata-editor")
            }
        }
        tideStackDevelopConfigContracts.forEach { asset ->
            from(asset) {
                into("ouroboros-ide-contracts")
            }
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
