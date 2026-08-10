# Changelog

All notable changes to the TideStack Develop IntelliJ Plugin will be documented in this file.

## Unreleased

### Changed
- TideStack Develop Config local private file now stores only `devKey`.
- Profile ID and container ID are resolved at runtime from `devKey` and `appName`; they are no longer manual settings.
- Settings UI replaces manual container refresh/storage with a "Resolve Develop Target" action.
- Deploy & Debug resolves profile/container before build/upload/restart/debug session creation.

## [0.1.0] - 2026-04-12

### Added

#### Configuration Management
- **Container ID Field**: Added configurable container ID field to settings
- **Configuration Import**: Implemented JSON file import for quick setup
  - File chooser dialog for `plugin-config.json`
  - Automatic parsing and validation
  - Success confirmation with masked credentials
- **Connection Testing**: Added "Test Connection" button to verify platform accessibility
- **Container Discovery**: Added "Refresh Containers" button to query and list available containers

#### API Client Enhancements
- **Container Query API**: Implemented `getContainers()` method
  - Queries all containers from platform
  - Filters by application name and profile ID
  - Returns container metadata (id, name, status, appName, profile)
- **Connection Test API**: Implemented `testConnection()` method
  - Validates platform URL reachability
  - Checks API endpoint availability
- **Enhanced Error Handling**: Improved error messages with HTTP status codes

#### Intelligent Container Resolution
- **Auto-Detection Logic**: Implemented smart container selection
  - Uses configured container ID if present
  - Queries platform for containers if ID is empty
  - Filters by appName and profileId
  - Prefers running containers over stopped ones
  - Falls back to first available container
- **User Notifications**: Added informative messages for container selection

#### User Experience Improvements
- **Validation Messages**: Added comprehensive validation for all settings
- **Progress Indicators**: Enhanced progress messages for each deployment step
- **Error Feedback**: Improved error messages with actionable guidance
- **Auto-Selection**: Automatically selects first running container after refresh

### Changed

#### Deploy & Debug Action
- **Removed Hard-Coded Container ID**: Replaced `"test-container"` with dynamic resolution
- **Enhanced Workflow**: Added container resolution step before restart
- **Better Error Handling**: Improved error messages for each step
- **Validation**: Added pre-flight validation for all required settings

#### Configuration UI
- **Improved Layout**: Reorganized settings into logical groups
- **Better Comments**: Added helpful hints for each field
- **Quick Actions**: Grouped import, test, and refresh buttons together

### Fixed
- **Container ID Issue**: Fixed hard-coded container ID that prevented deployment to actual containers
- **Missing Configuration**: Fixed inability to import configuration from platform
- **No Validation**: Fixed lack of connection testing before deployment
- **Manual Lookup**: Fixed requirement to manually find and enter container ID

### Technical Details

**Code Statistics**:
- Total Lines: 805 (up from 503, +60%)
- New Methods: 5
- Enhanced Methods: 3
- New Data Classes: 4

**New Components**:
- `OuroborosConfigurable.importFromFile()` - JSON configuration import
- `OuroborosConfigurable.testConnection()` - Platform connectivity test
- `OuroborosConfigurable.refreshContainers()` - Container discovery
- `OuroborosApiService.testConnection()` - Connection validation API
- `OuroborosApiService.getContainers()` - Container query API
- `DeployAndDebugAction.resolveContainerId()` - Intelligent container resolution
- `DeployAndDebugAction.validateSettings()` - Pre-flight validation

**Enhanced Components**:
- `TideStackDevelopConfigService` - Added project shared and local private config files
- `OuroborosApiService` - Added container and connection APIs
- `DeployAndDebugAction` - Replaced hard-coded ID with dynamic resolution

**New Data Classes**:
- `OuroborosApiService.Container` - Container metadata
- `OuroborosApiService.ApiResult` - API response wrapper
- `OuroborosApiService.AppContainer` - Application container list
- `OuroborosApiService.ContainerChild` - Container child info
- `OuroborosConfigurable.ImportedConfig` - Import configuration model

---

## Initial Prototype - 2026-04-11

### Added
- Basic plugin structure and configuration
- Settings persistence (development server URL, application name, profile ID, devKey)
- Configuration UI in Settings → Tools
- API client for platform integration
- Build service (Maven and Gradle support)
- Notification system
- Remote debug service
- Deploy & Debug action (toolbar button)
- One-click deployment workflow

### Known Issues
- Hard-coded container ID (`"test-container"`)
- No configuration import functionality
- No connection testing
- No container discovery
- Manual container ID lookup required

---

## Version Notes

### 0.1.0 milestone

**Functionality**: +60%
**Code Size**: +302 lines
**User Experience**: Significantly improved
**Production Ready**: No; still treated as an early IDE integration release.

**Key Improvements**:
1. Dynamic container resolution (was: hard-coded)
2. Configuration import (was: manual entry)
3. Connection testing (was: none)
4. Container discovery (was: manual lookup)
5. Intelligent matching (was: fixed ID)
6. Comprehensive validation (was: minimal)

---

## Upgrade Guide

### From initial prototype to 0.1.0

**Breaking Changes**: None (backward compatible)

**New Features**:
1. Container ID field is now optional (auto-detected if empty)
2. Import button available for quick setup
3. Test Connection button for validation
4. Refresh Containers button for discovery

**Migration Steps**:
1. Update plugin to v0.1.0
2. Open Settings → Tools → Ouroboros
3. (Optional) Click "Import from File" to import configuration
4. (Optional) Click "Refresh Containers" to auto-populate Container ID
5. Click "Apply" to save

**Recommended Workflow**:
- Use "Import from File" for initial setup
- Use "Refresh Containers" to update container ID
- Leave Container ID empty for auto-detection
- Use "Test Connection" to verify before deployment
