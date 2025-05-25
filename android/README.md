# 📱 Catamaran Family Safety - Android App

A comprehensive Android monitoring application that tracks phone activity for family safety purposes. This app monitors call logs and SMS activity (count only, no content) and syncs data to the Catamaran backend API.

## 🚀 Features

### Core Monitoring
- **Call Log Monitoring**: Tracks incoming, outgoing, and missed calls with duration
- **SMS Activity Tracking**: Monitors sent/received SMS count (no content access)
- **Contact Resolution**: Resolves phone numbers to contact names when available
- **Real-time Detection**: Uses ContentObserver for immediate activity detection

### Background Services
- **Foreground Service**: Continuous monitoring with persistent notification
- **Sync Service**: Automatic data upload every 15 minutes using WorkManager
- **Boot Receiver**: Automatically restarts monitoring after device reboot
- **Offline Storage**: Local Room database for reliable data persistence

### Senior-Friendly UI
- **Large Buttons**: 60-80dp height buttons for easy interaction
- **Clear Status**: Visual indicators for login, permissions, and monitoring status
- **Simple Controls**: One-tap start/stop monitoring
- **Permission Guidance**: Step-by-step permission setup

### Backend Integration
- **JWT Authentication**: Secure login with token-based auth
- **Batch Upload**: Efficient data synchronization
- **Retry Logic**: Automatic retry on network failures
- **Offline Handling**: Queues data when offline, syncs when connected

## 🏗️ Architecture

### Package Structure
```
com.catamaran.familysafety/
├── data/                    # Data models and Room database
│   ├── CallLogEntry.kt     # Call log entity
│   ├── SmsLogEntry.kt      # SMS log entity
│   ├── AppDatabase.kt      # Room database and DAOs
│   ├── CallLogReader.kt    # Call log content provider access
│   └── SmsCounter.kt       # SMS content provider access
├── network/                # API communication
│   ├── ApiService.kt       # Retrofit interface
│   └── ApiClient.kt        # HTTP client with auth
├── service/                # Background services
│   ├── MonitoringService.kt # Foreground monitoring service
│   ├── SyncService.kt      # Data sync service
│   └── BootReceiver.kt     # Boot completion receiver
├── ui/                     # User interface
│   └── MainActivity.kt     # Main activity
└── utils/                  # Utility classes
    ├── ContactMatcher.kt   # Phone number to contact resolution
    └── DataCollector.kt    # Activity data aggregation
```

### Key Components

#### MonitoringService
- Runs as foreground service with persistent notification
- Registers ContentObserver for call log and SMS changes
- Collects new activity data every 5 minutes
- Handles service lifecycle and cleanup

#### SyncService & SyncWorker
- Uses WorkManager for reliable background sync
- Uploads unsynced data to backend API
- Implements exponential backoff on failures
- Respects network constraints

#### DataCollector
- Aggregates call and SMS activity data
- Manages local Room database storage
- Handles data cleanup (30-day retention)
- Provides sync status tracking

## 🔧 Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API 23+ (Android 6.0+)
- Kotlin 1.8.20+
- Backend API running (see backend README)

### Installation

1. **Open in Android Studio**
   ```bash
   cd android
   # Open this directory in Android Studio
   ```

2. **Sync Gradle**
   - Android Studio will automatically sync dependencies
   - Ensure all dependencies download successfully

3. **Configure Backend URL**
   - Edit `ApiClient.kt` line 16:
   ```kotlin
   private val baseUrl = "http://YOUR_BACKEND_URL:3001/"
   ```
   - For emulator: `http://10.0.2.2:3001/`
   - For device: `http://YOUR_LOCAL_IP:3001/`

4. **Build and Install**
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Required Permissions

The app requires these permissions for monitoring:
- `READ_CALL_LOG` - Access call history
- `READ_SMS` - Access SMS logs (count only)
- `READ_CONTACTS` - Resolve phone numbers to names
- `READ_PHONE_STATE` - Phone state information
- `INTERNET` - Network access for sync
- `FOREGROUND_SERVICE` - Background monitoring
- `RECEIVE_BOOT_COMPLETED` - Auto-start after reboot

## 📱 Usage

### First Time Setup
1. **Grant Permissions**: Tap "Grant Permissions" and allow all requested permissions
2. **Login**: Tap "Login" (uses demo credentials: grandma@example.com / password123)
3. **Start Monitoring**: Tap "Start Monitoring" to begin activity tracking

### Daily Operation
- App runs in background with persistent notification
- Data syncs automatically every 15 minutes
- No user interaction required once started
- Export data anytime using "Export Data" button

### Stopping Monitoring
- Tap "Stop Monitoring" to pause activity tracking
- Data remains stored locally
- Can restart monitoring anytime

## 🔒 Privacy & Security

### Data Collection
- **Call Logs**: Phone number, contact name, duration, timestamp, call type
- **SMS Logs**: Sender/recipient number, contact name, timestamp, message type
- **No Content**: SMS message content is never accessed or stored
- **Local Storage**: All data encrypted in local Room database

### Data Transmission
- **Secure API**: All data transmitted over HTTPS
- **JWT Authentication**: Token-based authentication
- **Hashed Numbers**: Phone numbers hashed before transmission
- **Encrypted Names**: Contact names encrypted in transit

### Data Retention
- **Local**: 30 days automatic cleanup
- **Backend**: Configurable retention policy
- **User Control**: Export and delete data anytime

## 🧪 Testing

### Manual Testing
1. **Make Test Calls**: Call any number to generate call logs
2. **Send Test SMS**: Send SMS to generate activity
3. **Check Sync**: Verify data appears in backend dashboard
4. **Test Offline**: Disable network, generate activity, re-enable to test sync

### Debug Logging
Enable verbose logging in `ApiClient.kt`:
```kotlin
.addInterceptor(HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
})
```

### Monitoring Logs
```bash
adb logcat | grep -E "(MonitoringService|SyncWorker|DataCollector)"
```

## 🚀 Deployment

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
1. **Create Keystore**:
   ```bash
   keytool -genkey -v -keystore catamaran-release.keystore -alias catamaran -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Configure Signing** in `app/build.gradle`:
   ```gradle
   signingConfigs {
       release {
           storeFile file('catamaran-release.keystore')
           storePassword 'your_store_password'
           keyAlias 'catamaran'
           keyPassword 'your_key_password'
       }
   }
   ```

3. **Build Release**:
   ```bash
   ./gradlew assembleRelease
   ```

### Play Store Preparation
- Update version code/name in `build.gradle`
- Add app icon and screenshots
- Create privacy policy
- Test on multiple devices
- Submit for review

## 🔧 Configuration

### Sync Frequency
Modify sync interval in `SyncService.kt`:
```kotlin
val syncWork = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
```

### Data Retention
Adjust cleanup period in `DataCollector.kt`:
```kotlin
val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
```

### API Timeout
Configure network timeouts in `ApiClient.kt`:
```kotlin
.connectTimeout(30, TimeUnit.SECONDS)
.readTimeout(30, TimeUnit.SECONDS)
```

## 🐛 Troubleshooting

### Common Issues

**Permissions Denied**
- Ensure all permissions granted in Settings > Apps > Family Safety Monitor > Permissions
- Some manufacturers require additional battery optimization exemptions

**Sync Failures**
- Check network connectivity
- Verify backend API is running
- Check API URL configuration
- Review authentication tokens

**Service Stops**
- Add app to battery optimization whitelist
- Disable "Adaptive Battery" for the app
- Check manufacturer-specific battery settings

**No Activity Detected**
- Verify permissions are granted
- Check if ContentObserver is registered
- Test with actual calls/SMS (not simulated)

### Debug Commands
```bash
# Check app permissions
adb shell dumpsys package com.catamaran.familysafety | grep permission

# View app logs
adb logcat | grep FamilySafety

# Check service status
adb shell dumpsys activity services | grep MonitoringService
```

## 📄 License

This project is part of the Catamaran Family Safety system. See main project LICENSE for details.

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📞 Support

For technical support or questions:
- Create an issue in the GitHub repository
- Check the troubleshooting section above
- Review Android documentation for ContentProvider and WorkManager 