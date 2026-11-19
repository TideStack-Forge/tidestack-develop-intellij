import groovy.json.JsonSlurper

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.16.0"
}

group = "com.ouroboros"
version = "0.3.1"

val metadataRuntimeDir = file("common/metadata-editor-runtime/runtime")
val metadataEditorRuntimeDir = file("common/metadata-editor-runtime")
val metadataEditorSchemaDir = file("common/metadata-editor-schema")
val metadataEditorAssetManifest = metadataEditorSchemaDir.resolve("metadataEditorAssets.json")
val metadataEditorAssets = JsonSlurper().parse(metadataEditorAssetManifest) as Map<*, *>
val metadataHostRuntime = file("common/metadata-editor-runtime/dist/browser/metadata-editor-host.js")
val tideStackI18nDir = file("common/metadata-editor-schema/dist/i18n")
val amisSdkDir = file("common/amis-sdk")
val xlsxRuntimeDir = file("common/xlsx-runtime")
fun metadataEditorAssetList(key: String): List<String> {
    return (metadataEditorAssets[key] as? List<*>)?.map { it.toString() } ?: emptyList()
}
val metadataEditorBundleDefinitions = (metadataEditorAssets["editorBundles"] as? List<*>)
    ?.mapNotNull { it as? Map<*, *> }
    ?: emptyList()

val metadataRuntimeAssets = metadataEditorAssetList("runtimeFiles").map(metadataEditorRuntimeDir::resolve)
val metadataSchemaAssets = metadataEditorAssetList("schemaFiles").map { file("common/metadata-editor-schema/dist/$it") }
val metadataEditorIntegrationAssets = metadataEditorAssetList("editorIntegrationFiles").map { file("common/metadata-editor-schema/dist/$it") }
val metadataEditorBundleAssets = metadataEditorBundleDefinitions.flatMap { bundle ->
    val sourceRoot = file("common/metadata-editor-runtime/vendor").resolve(bundle["sourceRoot"].toString())
    (bundle["requiredFiles"] as? List<*>)?.map { sourceRoot.resolve(it.toString()) } ?: emptyList()
}
val tideStackDevelopConfigContracts = metadataEditorAssetList("contractFiles").map { file("common/contracts/$it") }
val tideStackI18nAssets = metadataEditorAssetList("i18nFiles").map(tideStackI18nDir::resolve)
val amisSdkRequiredAssets = metadataEditorAssetList("amisSdkRequiredFiles").map(amisSdkDir::resolve)
val xlsxRuntimeAssets = metadataEditorAssetList("xlsxRuntimeFiles").map(xlsxRuntimeDir::resolve)
val requiredMetadataEditorAssets = listOf(
    metadataHostRuntime,
    amisSdkDir.resolve("thirds/@fortawesome/fontawesome-free/webfonts/fa-solid-900.woff2")
) + metadataRuntimeAssets + metadataSchemaAssets + metadataEditorIntegrationAssets + metadataEditorBundleAssets + tideStackDevelopConfigContracts + tideStackI18nAssets + amisSdkRequiredAssets + xlsxRuntimeAssets

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.networknt:json-schema-validator:3.0.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.9.3")
}

sourceSets.test {
    resources.srcDir(metadataEditorSchemaDir.resolve("tests/fixtures"))
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
            include("src/**", "package.json", "tsconfig*.json", "vite.browser-host.config.ts", "vite.amis-editor.config.ts", "scripts/**")
        })
        inputs.files(fileTree(metadataEditorSchemaDir) {
            include("metadata-types/**", "schemas/**", "metadata-editors/**", "editor-schemas/**", "i18n/**", "tidestackDevelopCapabilities.json", "package.json", "scripts/**")
            include("metadataCreationContract.json", "metadataCreationCases.json", "metadataEditorAssets.json")
        })
        metadataEditorBundleDefinitions.forEach { bundle ->
            inputs.files(fileTree(file("common/metadata-editor-runtime/vendor").resolve(bundle["sourceRoot"].toString()).parentFile) {
                include("src/**", "public/**", "package.json", "webpack.config.js", "babel.config.js")
            })
        }
        outputs.file(metadataHostRuntime)
        outputs.files(metadataSchemaAssets)
        outputs.files(metadataEditorIntegrationAssets)
        outputs.files(metadataEditorBundleAssets)
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
        inputs.file("testing/common/fixtures/data/ide-metadata-workspace/documents.json")
        inputs.file(metadataEditorSchemaDir.resolve("tests/fixtures/metadata-schema-conformance.json"))
        if (!providers.gradleProperty("metadataJcef").isPresent) {
            outputs.dir("build/ide-metadata-fixture/intellij")
            exclude("**/MetadataJcefSmokeTest*")
        } else {
            systemProperty("java.awt.headless", "false")
            systemProperty("ide.browser.jcef.enabled", "true")
            systemProperty("ide.browser.jcef.testMode.enabled", "true")
        }
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
        metadataEditorIntegrationAssets.forEach { asset ->
            from(asset) {
                into("ouroboros-metadata-editor/editor-integrations")
            }
        }
        metadataEditorBundleDefinitions.forEach { bundle ->
            from(file("common/metadata-editor-runtime/vendor").resolve(bundle["sourceRoot"].toString())) {
                into("ouroboros-metadata-editor/${bundle["targetRoot"]}")
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
        xlsxRuntimeAssets.forEach { asset ->
            from(asset) {
                into("ouroboros-metadata-editor/xlsx")
            }
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
