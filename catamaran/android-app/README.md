# Catamaran Family Safety - Android App

A comprehensive Android application for family safety monitoring that tracks phone activity and syncs data with the Catamaran backend.

## Features

- **Senior-Friendly UI**: Large buttons (80dp height) and clear text for easy use
- **Phone Activity Monitoring**: Tracks call logs and SMS messages
- **Background Sync**: Automatically syncs data with Railway backend
- **Permissions Management**: Handles required permissions gracefully
- **Offline Storage**: Uses Room database for local data storage
- **Emergency Contact**: Quick access to emergency contact calling

## Architecture

### Key Components

- **MainActivity**: Main interface with large, senior-friendly buttons
- **MonitoringService**: Background foreground service for continuous monitoring
- **DataSyncWorker**: WorkManager worker for periodic data synchronization
- **CallLogMonitor**: Reads and processes call history
- **SMSMonitor**: Reads and processes SMS messages
- **Room Database**: Local storage for call logs and SMS data
- **Retrofit API**: Communication with Railway backend

### Project Structure

```
app/src/main/java/com/catamaran/familysafety/
├── ui/                     # Activities and UI components
│   ├── MainActivity.kt     # Main app interface
│   └── SettingsActivity.kt # Settings (placeholder)
├── service/                # Background services
│   └── MonitoringService.kt # Foreground monitoring service
├── worker/                 # WorkManager workers
│   └── DataSyncWorker.kt   # Data synchronization
├── monitor/                # Data collection
│   ├── CallLogMonitor.kt   # Call log reading
│   └── SMSMonitor.kt       # SMS reading
├── data/                   # Data layer
│   ├── model/              # Data models
│   ├── database/           # Room database
│   └── repository/         # Repository pattern
├── network/                # API communication
│   └── ApiService.kt       # Retrofit interface
├── viewmodel/              # ViewModels
│   ├── MainViewModel.kt    # Main activity logic
│   └── MainViewModelFactory.kt
├── utils/                  # Utilities
│   └── PreferenceManager.kt # SharedPreferences wrapper
├── receiver/               # Broadcast receivers
│   └── BootReceiver.kt     # Boot completion handler
└── CatamaranApplication.kt # Application class
```

## Required Permissions

The app requires the following permissions for monitoring:

- `READ_CALL_LOG`: Access call history
- `READ_SMS`: Access SMS messages
- `READ_CONTACTS`: Identify contact names
- `INTERNET`: Sync data with backend
- `WAKE_LOCK`: Keep device awake for background tasks
- `FOREGROUND_SERVICE`: Run monitoring service
- `RECEIVE_BOOT_COMPLETED`: Restart monitoring after reboot

## Building the Project

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK API 24+ (Android 7.0)
- Kotlin 1.9.10+

### Build Steps

1. **Clone the repository**:
   ```bash
   git clone https://github.com/lisafeets/catamaran.git
   cd catamaran/android-app
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to `catamaran/android-app`

3. **Sync Gradle**:
   - Android Studio will automatically sync Gradle files
   - Wait for dependencies to download

4. **Build the project**:
   ```bash
   ./gradlew assembleDebug
   ```

5. **Install on device**:
   ```bash
   ./gradlew installDebug
   ```

## Configuration

### Backend URL

The app is configured to connect to the Railway backend:
```
https://catamaran-production-3422.up.railway.app
```

This can be changed in `ApiService.kt` or through the PreferenceManager.

### Sync Frequency

Default sync frequency is 30 minutes. This can be configured in the PreferenceManager.

## Key Features Implementation

### Senior-Friendly Design

- **Large Buttons**: All primary buttons are 80dp height
- **High Contrast**: Clear color scheme with good contrast
- **Simple Layout**: Minimal, uncluttered interface
- **Large Text**: 20sp+ for primary text, 16sp+ for secondary

### Background Monitoring

- **Foreground Service**: Ensures monitoring continues even when app is closed
- **WorkManager**: Handles periodic data sync with retry logic
- **Boot Receiver**: Automatically restarts monitoring after device reboot

### Data Privacy

- **Local Storage**: All data stored locally first
- **Selective Sync**: Only unsynced data is sent to backend
- **Backup Exclusion**: Sensitive data excluded from Android backups

## Testing

### Unit Tests

Run unit tests:
```bash
./gradlew test
```

### Instrumented Tests

Run on connected device:
```bash
./gradlew connectedAndroidTest
```

### Manual Testing

1. **Permission Flow**: Test permission requests on first launch
2. **Monitoring Toggle**: Verify monitoring starts/stops correctly
3. **Background Sync**: Check data sync in background
4. **Boot Restart**: Test monitoring restart after device reboot

## Deployment

### Debug Build

```bash
./gradlew assembleDebug
```

### Release Build

1. Configure signing in `app/build.gradle`
2. Build release APK:
   ```bash
   ./gradlew assembleRelease
   ```

## Troubleshooting

### Common Issues

1. **Permissions Denied**: Ensure all required permissions are granted
2. **Sync Failures**: Check network connectivity and backend status
3. **Service Not Starting**: Verify battery optimization settings
4. **Data Not Appearing**: Check if monitoring is enabled and permissions granted

### Logs

View app logs:
```bash
adb logcat | grep "Catamaran"
```

## Contributing

1. Follow Android development best practices
2. Use MVVM architecture pattern
3. Write unit tests for new features
4. Follow Kotlin coding conventions
5. Update documentation for new features

## License

This project is part of the Catamaran Family Safety system. 