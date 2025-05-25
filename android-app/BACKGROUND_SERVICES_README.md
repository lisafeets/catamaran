# Catamaran Android Background Services

## Overview

The Catamaran Android app implements a production-ready background monitoring system that continuously tracks phone activity (calls and SMS) while maintaining strict privacy standards and battery optimization. The system is designed to run 24/7 reliably without draining battery or causing performance issues.

## Architecture

### Core Components

```
BackgroundMonitorService (Foreground Service)
├── CallLogWatcher - monitors call_log changes
├── SMSWatcher - monitors SMS provider changes  
├── ContactResolver - matches numbers to names
├── LocalDatabase - stores data before sync
└── SyncScheduler - triggers uploads to backend
```

### Service Hierarchy

1. **BackgroundMonitorService** - Main foreground service
2. **DataSyncService** - Handles backend synchronization
3. **WorkManager Workers** - Reliable background task execution
4. **Utility Classes** - Network, encryption, contact resolution

## Key Features

### 🔒 Privacy & Security
- **No SMS content stored** - Only metadata (count, timestamp, frequency)
- **End-to-end encryption** - All data encrypted before storage
- **Certificate pinning** - Secure API communication
- **Local data cleanup** - Automatic removal of old synced data

### 🔋 Battery Optimization
- **Foreground service** with persistent notification
- **Adaptive sync intervals** based on network type and activity
- **Efficient content observers** for real-time monitoring
- **Smart batching** to minimize network requests
- **Battery optimization exclusion** guidance for users

### 🌐 Network Management
- **Network-aware scheduling** (WiFi vs cellular)
- **Offline/online scenarios** handled gracefully
- **Exponential backoff** for failed sync attempts
- **Data compression** to minimize bandwidth usage
- **Retry logic** with intelligent failure handling

### 📊 Monitoring & Analytics
- **Real-time statistics** on sync performance
- **Risk assessment** for suspicious activity
- **Health checks** to ensure service reliability
- **Comprehensive logging** for debugging

## Implementation Details

### Starting the Service

```kotlin
// Start monitoring service
BackgroundMonitorService.startService(context)

// Stop monitoring service
BackgroundMonitorService.stopService(context)

// Force immediate sync
BackgroundMonitorService.forceSyncData(context)
```

### Service Configuration

The service automatically configures itself based on:
- Network type (WiFi, Cellular, Ethernet)
- Battery level and optimization settings
- Activity patterns and data volume
- Device capabilities and Android version

### Sync Intervals

| Condition | Sync Interval |
|-----------|---------------|
| WiFi + High Activity | 5 minutes |
| WiFi + Normal Activity | 15 minutes |
| Cellular + Normal Activity | 30 minutes |
| Low Battery | 60 minutes |

### Data Flow

1. **Content Observers** detect new calls/SMS
2. **Watchers** process and encrypt data
3. **Local Database** stores encrypted data
4. **SyncScheduler** determines when to sync
5. **DataSyncService** uploads to backend
6. **NetworkUtils** handles secure transmission

## Required Permissions

### Manifest Permissions
```xml
<!-- Core monitoring permissions -->
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.READ_CONTACTS" />

<!-- Background service permissions -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Network permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Battery optimization -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

### Runtime Permissions
The app requests these permissions at runtime:
- `READ_CALL_LOG` - Required for call monitoring
- `READ_SMS` - Required for SMS monitoring  
- `READ_CONTACTS` - Required for contact resolution

## Battery Optimization Setup

### User Guidance
The app provides step-by-step guidance to exclude it from battery optimization:

1. **Automatic Detection** - Checks if optimization is disabled
2. **Device-Specific Instructions** - Tailored for each manufacturer
3. **Fallback Options** - General Android settings if specific ones fail

### Supported Manufacturers
- Xiaomi (MIUI)
- Oppo (ColorOS)
- Vivo (FuntouchOS)
- Huawei (EMUI)
- Samsung (One UI)
- OnePlus (OxygenOS)

## Database Schema

### CallLogEntity
```kotlin
data class CallLogEntity(
    val id: String,
    val phoneNumber: String, // Encrypted
    val contactName: String?, // Encrypted
    val duration: Long,
    val callType: String,
    val timestamp: Long,
    val isKnownContact: Boolean,
    val riskScore: Float,
    val suspiciousPatterns: String?,
    val syncStatus: SyncStatus
)
```

### SmsLogEntity
```kotlin
data class SmsLogEntity(
    val id: String,
    val phoneNumber: String, // Encrypted
    val contactName: String?, // Encrypted
    val messageCount: Int, // NO CONTENT STORED
    val smsType: SmsType,
    val timestamp: Long,
    val isKnownContact: Boolean,
    val riskScore: Float,
    val frequencyPattern: String?, // Encrypted
    val syncStatus: SyncStatus
)
```

## API Integration

### Endpoints
- `POST /v1/sync/call-logs` - Upload call log data
- `POST /v1/sync/sms-logs` - Upload SMS log data
- `POST /v1/sync/batch` - Batch upload endpoint

### Authentication
```kotlin
headers = mapOf(
    "Authorization" to "Bearer ${authToken}",
    "Content-Type" to "application/json",
    "X-Device-ID" to deviceId,
    "X-User-ID" to userId
)
```

### Data Format
```json
{
  "deviceId": "device_123",
  "userId": "user_123",
  "timestamp": 1640995200000,
  "callLogs": [...],
  "smsLogs": [...]
}
```

## Monitoring & Debugging

### Service Status
```kotlin
// Check service state
val serviceState = backgroundMonitorService.serviceState.value

// Get network state
val networkState = backgroundMonitorService.networkState.value

// Get sync statistics
val syncStats = syncScheduler.getSyncStats()
```

### Logging
The system uses structured logging with different levels:
- `DEBUG` - Detailed operation logs
- `INFO` - Important state changes
- `WARNING` - Recoverable issues
- `ERROR` - Critical failures

### Health Checks
Automatic health checks every 5 minutes verify:
- Permission status
- Database connectivity
- Network availability
- Service responsiveness

## Troubleshooting

### Common Issues

#### Service Stops Running
1. Check battery optimization settings
2. Verify all required permissions
3. Check for Android background restrictions
4. Review device-specific power management

#### Sync Failures
1. Verify network connectivity
2. Check API endpoint availability
3. Review authentication tokens
4. Check data encryption/decryption

#### High Battery Usage
1. Review sync intervals
2. Check for excessive retries
3. Verify efficient content observers
4. Monitor network usage patterns

### Debug Commands
```kotlin
// Force immediate sync
BackgroundMonitorService.forceSyncData(context)

// Clear contact cache
contactResolver.clearCache()

// Get detailed statistics
val stats = dataSyncService.getSyncStatistics()
```

## Performance Considerations

### Memory Management
- **LRU Cache** for contact resolution (1000 entries max)
- **Automatic cleanup** of expired cache entries
- **Efficient database queries** with proper indexing
- **Batch processing** to reduce memory footprint

### Network Optimization
- **Data compression** using GZIP
- **Certificate pinning** for security
- **Adaptive retry logic** with exponential backoff
- **Network type awareness** for scheduling

### Storage Management
- **Encrypted local database** using SQLCipher
- **Automatic cleanup** of old synced data (30 days)
- **Efficient indexing** for fast queries
- **Minimal data storage** (metadata only)

## Security Considerations

### Data Protection
- **AES-256 encryption** for all sensitive data
- **Android Keystore** integration
- **No plaintext storage** of phone numbers or names
- **Secure key management** and rotation

### Network Security
- **TLS 1.3** for all communications
- **Certificate pinning** to prevent MITM attacks
- **Request signing** for API authentication
- **Secure token storage** using EncryptedSharedPreferences

### Privacy Compliance
- **No SMS content** ever stored or transmitted
- **Minimal data collection** (metadata only)
- **User consent** for all monitoring activities
- **Data retention policies** with automatic cleanup

## Testing

### Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Integration Tests
```bash
./gradlew connectedAndroidTest
```

### Manual Testing
1. Install app and grant permissions
2. Exclude from battery optimization
3. Make test calls and send SMS
4. Verify data appears in dashboard
5. Check sync status and statistics

## Deployment

### Release Build
```bash
./gradlew assembleRelease
```

### ProGuard Configuration
The release build includes:
- Code obfuscation
- Resource shrinking
- Security hardening
- Performance optimization

## Support

For technical support or questions about the background services:
1. Check the logs for error messages
2. Review the troubleshooting section
3. Verify all permissions and settings
4. Contact the development team with specific error details

---

**Note**: This background monitoring system is designed for family safety and requires explicit user consent. All data is encrypted and handled according to strict privacy standards. 