# TideStack Develop for IntelliJ IDEA

TideStack Develop connects IntelliJ IDEA with the TideStack development platform. It provides visual JSON metadata editing, project binding to a development platform, and one-click deploy-and-debug for Java extension projects.

Plugin identifier: `tidestack.develop`.

## Highlights

- Visual editors for TideStack JSON metadata files.
- Dedicated UI Schema editor powered by the shared AMIS visual editor runtime.
- Support for both the new `src/main/metadata` convention and the legacy `resources/META-INF/ouroboros` convention.
- Built-in `New > TideStack Metadata` templates for all supported metadata types.
- TideStack Develop Config for project-to-platform binding.
- One-click Deploy & Debug from IntelliJ IDEA.
- Automatic JAR discovery, upload, container restart, debug session creation, and Remote JVM Debug attachment.

## Supported Metadata Files

The plugin opens a `可视化编辑` tab for supported metadata JSON files and keeps the normal text editor available as a separate tab.

Use `New > TideStack Metadata` from the Project view to create a new metadata JSON file from the built-in templates. The plugin opens one form for the file path and template fields, and the templates use the new `src/main/metadata` convention by default.

| Metadata type | New file convention | Legacy path support |
|---|---|---|
| Authority | `.authority.json` | `resources/META-INF/ouroboros/authority/**/*.json` |
| Menu | `.menu.json` | `resources/META-INF/ouroboros/menu-model/**/*.json` |
| Development menu | `.dev-menu.json` | `resources/META-INF/ouroboros/dev-menu-model/**/*.json` |
| UI model | `.ui-model.json` | `resources/META-INF/ouroboros/ui-model/**/*.json` |
| UI Schema | `.ui-schema.json` | `resources/META-INF/ouroboros/ui-schema/**/*.json` |
| App module | `src/main/metadata/app-modules.json` | `resources/META-INF/ouroboros/app-modules.json` |
| Configuration | `src/main/metadata/configuration.json` | `resources/META-INF/ouroboros/configuration.json` |
| Configuration group | `src/main/metadata/configuration-groups.json` | `resources/META-INF/ouroboros/configuration-groups.json` |

## TideStack Develop Config

Open `Settings > Tools > TideStack Develop Config` to bind the current project to a development platform.

The plugin stores configuration in two project files:

- `.tidestack/develop.json`: shared project configuration, intended to be committed. It stores `developmentServerUrl` and `appName`.
- `.tidestack/develop.local.json`: local private configuration, intended to stay uncommitted. It stores `devKey` only.

`profileId` and `containerId` are not manual settings. The plugin uses `devKey` to authorize with the platform, resolves the active profile, then resolves the target container from `appName + profile`.

## Deploy & Debug

Use the toolbar action `Deploy & Debug` after the project is configured.

The workflow is:

1. Build the IntelliJ project.
2. Locate the latest Maven or Gradle JAR artifact.
3. Authorize the local `devKey` with `POST /api/dev/{appName}/authorize`.
4. Resolve the target development container.
5. Upload the JAR to the platform.
6. Restart the target container.
7. Create a remote debug session.
8. Attach IntelliJ IDEA's Remote JVM Debug configuration automatically.

## Requirements

- IntelliJ IDEA 2023.1 or later.
- Java 17 or later for plugin execution and project builds.
- A TideStack development platform that exposes the app-scoped dev APIs.
- A project with `.tidestack/develop.json` and local `.tidestack/develop.local.json`, or values configured from the settings page.

## Privacy And Data Storage

- The plugin reads and writes metadata files in the opened project.
- Shared platform binding data is written to `.tidestack/develop.json`.
- The local `devKey` is written only to `.tidestack/develop.local.json`.
- Derived runtime data such as `profileId` and `containerId` is resolved on demand and is not stored by the plugin.
- Network requests are sent only to the configured `developmentServerUrl`.

## Known Limitations

- The first release supports JSON metadata files only.
- UI Schema visual editing currently uses the light AMIS editor theme for better editor compatibility.
- Deploy & Debug expects a Maven or Gradle build output under standard `target/` or `build/libs/` locations.
