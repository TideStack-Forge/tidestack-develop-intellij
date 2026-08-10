# Ouroboros IDEA Plugin - Feature Documentation

## Overview

The Ouroboros IDEA Plugin provides seamless integration between IntelliJ IDEA and the Ouroboros development platform, enabling one-click deployment and remote debugging of Java applications.

**Version**: 0.1.0
**Total Code**: 805 lines of Kotlin
**Modules**: 7 core components

---

## Core Features

### 1. TideStack Develop Config

**Location**: `TideStackDevelopConfigService.kt`, `OuroborosConfigurable.kt`

**Project Shared Config** (`.tidestack/develop.json`):
- Development Server URL (e.g., `http://localhost:88`)
- Application Name

**Local Private Config** (`.tidestack/develop.local.json`):
- Dev Key (local development authentication key)

Profile ID and container ID are not manual configuration fields. The plugin resolves the current profile through the devKey, then resolves the target container from `appName + profile`.

**Configuration UI**:
- Settings → Tools → TideStack Develop Config
- Text fields with validation and helpful comments
- Password field for devKey security

### 2. Configuration Import

**Feature**: Import from JSON File

**Functionality**:
- File chooser dialog for `plugin-config.json`
- Automatic parsing and population of shared and local config files
- Migration from legacy `platformUrl` / `debugKey` fields to `developmentServerUrl` / `devKey`
- Error handling with detailed messages

**JSON Format**:
```json
{
  "developmentServerUrl": "http://localhost:88",
  "appName": "my-app",
  "devKey": "43339c49e483425f8b80c2652a49e664"
}
```

### 3. Connection Testing

**Feature**: Test Connection Button

**Functionality**:
- Validates platform URL reachability
- Checks API endpoint availability
- Provides immediate feedback on connection status
- Helps diagnose network/configuration issues

**Use Case**: Verify platform is accessible before attempting deployment

### 4. Container Management

**Feature**: Resolve Develop Target Button

**Functionality**:
- Authorizes the local devKey with the platform
- Resolves the current profile from the authorization response
- Filters containers by application name and resolved profile
- Displays the selected target container without saving derived IDs

**Container Information**:
- Container ID
- Container Name (format: `appName-profileId`)
- Status (running, stopped, paused, etc.)
- Application Name
- Profile

### 5. Intelligent Container Resolution

**Feature**: Auto-detect Target Container

**Logic**:
1. Authorize devKey and read profile from platform response
2. Query platform containers for `appName + profile`
3. Prefer running containers over stopped ones
4. Fall back to first available container if no running container found

**Benefits**:
- No manual container ID lookup required
- No manual profile ID entry required
- Always targets the correct container
- Handles multiple container scenarios gracefully

### 6. One-Click Deploy & Debug

**Feature**: Deploy & Debug Action (Toolbar Button)

**Workflow**:
1. **Build Project** - Triggers Maven/Gradle build
2. **Find JAR** - Locates built artifact (target/ or build/libs/)
3. **Resolve Target** - Authenticates with devKey and resolves profile/container
4. **Upload JAR** - Uploads to platform extend-jar endpoint
5. **Restart Container** - Restarts the resolved target container
6. **Create Debug Session** - Establishes remote debug tunnel
7. **Attach Debugger** - Creates and launches IDEA debug configuration

**Progress Tracking**:
- Background task with progress indicator
- Step-by-step status updates
- Detailed notifications for each phase
- Error handling with actionable messages

### 7. Build Service

**Location**: `BuildService.kt`

**Functionality**:
- Triggers IDEA project build (all modules)
- Supports both Maven and Gradle projects
- Searches for built JAR in standard locations:
  - Maven: `target/*.jar`
  - Gradle: `build/libs/*.jar`
- Filters out sources and javadoc JARs
- Selects most recently modified JAR
- 2-minute timeout with polling

### 8. API Client

**Location**: `OuroborosApiService.kt`

**Endpoints**:
- `GET /api/app/container` - List all containers
- `POST /api/dev/{appName}/authorize` - Authenticate with devKey and resolve profile
- `POST /api/dev/{appName}/extend-jar` - Upload JAR (multipart)
- `POST /api/app/container/{containerId}/restart` - Restart container
- `POST /api/dev/runtime/debug-sessions` - Create debug session

**Features**:
- OkHttp-based HTTP client
- Gson JSON serialization
- Result-based error handling
- Detailed error messages with HTTP status codes

### 9. Remote Debug Service

**Location**: `RemoteDebugService.kt`

**Functionality**:
- Creates IDEA Remote Debug Configuration
- Configures host and port from debug session
- Sets socket transport mode (attach to remote JVM)
- Reuses existing configuration if present
- Automatically launches debugger

**Configuration**:
- Name: `Ouroboros - {host}:{port}`
- Transport: Socket
- Mode: Attach (client mode)
- Debugger: Default Debug Executor

### 10. Notification System

**Location**: `NotificationService.kt`

**Notification Types**:
- **Info**: General progress updates
- **Success**: Completion messages
- **Warning**: Non-critical issues (e.g., missing configuration)
- **Error**: Failures requiring user action

**Display**:
- Balloon notifications in IDE
- Grouped under "Ouroboros"
- Non-intrusive, dismissible

---

## User Workflows

### Initial Setup

1. Open Settings → Tools → Ouroboros
2. Click "Import from File" and select `plugin-config.json`
3. Click "Test Connection" to verify platform access
4. Click "Resolve Develop Target" to verify devKey/profile/container resolution
5. Click "Apply" to save settings

### Daily Development

1. Make code changes in IDEA
2. Click "Deploy & Debug" toolbar button
3. Wait for deployment (30-60 seconds)
4. Debugger attaches automatically
5. Set breakpoints and debug as usual

### Troubleshooting

**Connection Issues**:
- Use "Test Connection" to verify platform URL
- Check network connectivity
- Verify platform is running

**Container Issues**:
- Use "Resolve Develop Target" to verify the selected container
- Verify appName matches the project binding on the platform
- Check container status (must be running or startable)

**Build Issues**:
- Ensure project builds successfully in IDEA
- Check Maven/Gradle configuration
- Verify JAR is generated in expected location

**Debug Issues**:
- Verify devKey is correct
- Check container has debug capability enabled
- Ensure no firewall blocking debug port

---

## Technical Details

### Dependencies

**Build System**: Gradle with Kotlin DSL
**IDEA Platform**: 2023.1+
**Java Version**: 17+
**Required Plugins**: Java, Maven, Gradle

**External Libraries**:
- OkHttp 4.x (HTTP client)
- Gson 2.x (JSON serialization)
- Kotlin Coroutines (async operations)

### Plugin Structure

```
ide-plugins/intellij-plugin/
├── src/main/kotlin/com/ouroboros/idea/
│   ├── actions/
│   │   └── DeployAndDebugAction.kt       (170 lines)
│   ├── services/
│   │   ├── BuildService.kt               (55 lines)
│   │   ├── NotificationService.kt        (33 lines)
│   │   ├── OuroborosApiService.kt        (230 lines)
│   │   └── RemoteDebugService.kt         (41 lines)
│   └── settings/
│       ├── OuroborosConfigurable.kt      (206 lines)
│       └── TideStackDevelopConfigService.kt
├── src/main/resources/META-INF/
│   └── plugin.xml                        (44 lines)
├── build.gradle.kts
└── settings.gradle.kts
```

### Extension Points

**Application Configurable**:
- Parent: Tools
- ID: `com.ouroboros.idea.settings.OuroborosConfigurable`
- Display Name: "Ouroboros"

**Notification Group**:
- ID: "Ouroboros"
- Display Type: Balloon

**Action**:
- ID: `com.ouroboros.idea.actions.DeployAndDebugAction`
- Text: "Deploy & Debug"
- Icon: Execute (play button)
- Location: Toolbar Run Group (after Run Configuration)

### Services

**Application-Level**:
- `TideStackDevelopConfigService` - Project shared and local private develop config storage
- `OuroborosApiService` - Platform API client

**Project-Level**:
- `BuildService` - Project build management
- `NotificationService` - User notifications
- `RemoteDebugService` - Debug configuration management

---

## Comparison: Before vs After

### Before (Initial Implementation)

❌ Hard-coded container ID (`"test-container"`)
❌ No configuration import
❌ No connection testing
❌ No container discovery
❌ Manual container ID lookup required
❌ No validation feedback
⚠️ 503 lines of code

### After (Complete Implementation)

✅ Dynamic container resolution
✅ JSON configuration import
✅ Connection testing
✅ Container list query and auto-selection
✅ Intelligent container matching
✅ Comprehensive validation and error messages
✅ 805 lines of code (+60% functionality)

---

## Future Enhancements

### Potential Features

1. **Multi-Container Support**: Deploy to multiple containers simultaneously
2. **Build Configuration**: Custom build commands and artifact paths
3. **Debug Session Management**: List and terminate active sessions
4. **Log Streaming**: Real-time container logs in IDEA console
5. **Performance Metrics**: Display container resource usage
6. **Deployment History**: Track and rollback deployments
7. **Environment Variables**: Configure container environment from IDEA
8. **Hot Reload**: Incremental updates without full restart

### Known Limitations

1. **Single Profile**: Only supports one profile per project
2. **JAR Only**: Does not support WAR or other artifact types
3. **No Rollback**: Cannot revert to previous deployment
4. **No Logs**: Container logs not visible in IDEA
5. **No Metrics**: No visibility into container performance

---

## Support

**Documentation**: See `QUICK_START.md` for setup instructions
**Issues**: Report bugs to development team
**Platform**: Requires Ouroboros Development Platform 1.0.0-beta.2+
