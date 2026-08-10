# IDEA Plugin Build Guide

## Prerequisites

### Required Software
- **JDK 17+**: Required for plugin compilation
- **Gradle 8.4+**: Build tool (or use wrapper)
- **IntelliJ IDEA 2023.1+**: For testing the plugin

### Check Prerequisites
```bash
java -version    # Should show 17 or higher
gradle -version  # Should show 8.4 or higher
```

---

## Build Methods

### Method 1: Using Gradle Wrapper (Recommended)

The project includes Gradle wrapper scripts, but requires `gradle-wrapper.jar`:

```bash
cd ide-plugins/intellij-plugin

# Download gradle-wrapper.jar (if missing)
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar

# Build plugin
./gradlew buildPlugin

# Output: build/distributions/tidestack-develop-0.1.0.zip
```

### Method 2: Using System Gradle

If you have Gradle installed via Homebrew or other package manager:

```bash
cd ide-plugins/intellij-plugin

# Install Gradle (macOS)
brew install gradle

# Build plugin
gradle buildPlugin

# Output: build/distributions/tidestack-develop-0.1.0.zip
```

### Method 3: Using IntelliJ IDEA

1. Open `intellij-plugin` folder in IntelliJ IDEA
2. Wait for Gradle sync to complete
3. Open Gradle tool window (View → Tool Windows → Gradle)
4. Navigate to: Tasks → intellij → buildPlugin
5. Double-click to run
6. Output: `build/distributions/tidestack-develop-0.1.0.zip`

---

## Build Tasks

### Common Tasks

```bash
# Build plugin distribution
./gradlew buildPlugin

# Run plugin in test IDE
./gradlew runIde

# Verify plugin structure
./gradlew verifyPlugin

# Run tests (if any)
./gradlew test

# Clean build artifacts
./gradlew clean
```

### Build Output

**Location**: `build/distributions/tidestack-develop-0.1.0.zip`

**Contents**:
- `lib/` - Plugin JAR and dependencies
- `META-INF/plugin.xml` - Plugin descriptor
- Kotlin runtime libraries
- OkHttp and Gson libraries

---

## Installation

### Method 1: Install from Disk

1. Open IntelliJ IDEA
2. Go to: Settings → Plugins
3. Click gear icon → Install Plugin from Disk
4. Select: `build/distributions/tidestack-develop-0.1.0.zip`
5. Click OK and restart IDEA

### Method 2: Install from Build Directory

1. Build plugin: `./gradlew buildPlugin`
2. In IDEA: Settings → Plugins → Install from Disk
3. Navigate to: `ide-plugins/intellij-plugin/build/distributions/`
4. Select: `tidestack-develop-0.1.0.zip`
5. Restart IDEA

---

## Verification

### After Installation

1. **Check Plugin Loaded**:
   - Settings → Plugins
   - Search for "Ouroboros"
   - Should show "Ouroboros Metadata Editor" as enabled

2. **Check Settings Available**:
   - Settings → Tools → Ouroboros
   - Should show configuration panel

3. **Check Toolbar Action**:
   - Main toolbar (Run group)
   - Should show "Deploy & Debug" button (play icon)

4. **Check Notification Group**:
   - Settings → Appearance & Behavior → Notifications
   - Should show "Ouroboros" group

---

## Troubleshooting

### Build Issues

**Problem**: `gradle-wrapper.jar not found`
```bash
# Solution: Download manually
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar
```

**Problem**: `Could not resolve dependencies`
```bash
# Solution: Clear Gradle cache and retry
rm -rf ~/.gradle/caches
./gradlew clean buildPlugin --refresh-dependencies
```

**Problem**: `Unsupported Java version`
```bash
# Solution: Check Java version
java -version  # Must be 17+

# Set JAVA_HOME if needed
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

**Problem**: `Kotlin compiler error`
```bash
# Solution: Clean and rebuild
./gradlew clean
./gradlew buildPlugin --no-daemon
```

### Installation Issues

**Problem**: Plugin not showing in IDEA
- Verify ZIP file is not corrupted
- Check IDEA version (must be 2023.1+)
- Try: Help → Find Action → "Clear Plugin Cache"

**Problem**: Settings not appearing
- Restart IDEA after installation
- Check: Settings → Plugins → Ouroboros (enabled)
- Check IDEA logs: Help → Show Log in Finder

**Problem**: Toolbar button missing
- View → Appearance → Toolbar (ensure enabled)
- Right-click toolbar → Customize Toolbar
- Check "Deploy & Debug" action is present

---

## Development

### Running in Development Mode

```bash
# Launch test IDE with plugin
./gradlew runIde

# This will:
# 1. Compile plugin
# 2. Launch new IDEA instance
# 3. Load plugin automatically
# 4. Allow testing without installation
```

### Debugging Plugin

1. Open `intellij-plugin` in IntelliJ IDEA
2. Create Run Configuration:
   - Type: Gradle
   - Task: `runIde`
   - Debug mode: Yes
3. Set breakpoints in plugin code
4. Run in debug mode
5. Test IDE launches with debugger attached

### Hot Reload

```bash
# Enable auto-reload during development
./gradlew runIde --continuous

# Changes to Kotlin files will trigger rebuild
# Test IDE will reload plugin automatically
```

---

## Build Configuration

### Key Files

**build.gradle.kts**:
- Plugin version: `0.1.0`
- IDEA version: `2023.1`
- Kotlin version: `1.9.0`
- Dependencies: OkHttp, Gson

**plugin.xml**:
- Plugin ID: `tidestack.develop`
- Plugin name: `TideStack Develop`
- Vendor: `TideStack`
- Description: IDE integration for platform

**gradle.properties**:
- Gradle version: `8.4`
- JVM target: `17`

### Customization

**Change Plugin Version**:
```kotlin
// build.gradle.kts
version = "0.1.1"
```

**Change IDEA Version**:
```kotlin
// build.gradle.kts
intellij {
    version.set("2023.2")
}
```

**Add Dependencies**:
```kotlin
// build.gradle.kts
dependencies {
    implementation("com.example:library:1.0.0")
}
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build Plugin

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build Plugin
        run: |
          cd ide-plugins/intellij-plugin
          ./gradlew buildPlugin
      - name: Upload Artifact
        uses: actions/upload-artifact@v3
        with:
          name: plugin
          path: ide-plugins/intellij-plugin/build/distributions/*.zip
```

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                dir('intellij-plugin') {
                    sh './gradlew clean buildPlugin'
                }
            }
        }
        stage('Archive') {
            steps {
                archiveArtifacts 'ide-plugins/intellij-plugin/build/distributions/*.zip'
            }
        }
    }
}
```

---

## Distribution

### Internal Distribution

1. Build plugin: `./gradlew buildPlugin`
2. Upload ZIP to internal server
3. Share download link with team
4. Users install via "Install from Disk"

### Plugin Repository (Future)

For public distribution:
1. Create JetBrains Marketplace account
2. Upload plugin ZIP
3. Fill plugin metadata
4. Submit for review
5. Users install via Marketplace

---

## Support

**Build Issues**: Check Gradle logs in `build/` directory
**Runtime Issues**: Check IDEA logs (Help → Show Log)
**Plugin Issues**: Enable debug logging in IDEA

**Logs Location**:
- macOS: `~/Library/Logs/JetBrains/IntelliJIdea2023.1/`
- Linux: `~/.cache/JetBrains/IntelliJIdea2023.1/log/`
- Windows: `%APPDATA%\JetBrains\IntelliJIdea2023.1\log\`
