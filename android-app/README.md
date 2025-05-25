# Catamaran Android App

The Catamaran Android app is designed for seniors to provide non-intrusive monitoring of phone activity while maintaining strict privacy and security standards.

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Catamaran Android App                    │
├─────────────────────────────────────────────────────────────┤
│  UI Layer (Activities/Fragments)                           │
│  ├── MainActivity (Dashboard)                              │
│  ├── SetupActivity (First-time setup)                      │
│  └── SettingsActivity (Privacy controls)                   │
├─────────────────────────────────────────────────────────────┤
│  ViewModel Layer (MVVM Pattern)                            │
│  ├── MainViewModel                                         │
│  ├── SetupViewModel                                        │
│  └── SettingsViewModel                                     │
├─────────────────────────────────────────────────────────────┤
│  Repository Layer (Data Management)                        │
│  ├── CallLogRepository                                     │
│  ├── SmsRepository                                         │
│  ├── UserRepository                                        │
│  └── FamilyRepository                                      │
├─────────────────────────────────────────────────────────────┤
│  Data Sources                                              │
│  ├── Local Database (Room + SQLCipher)                     │
│  ├── Remote API (Retrofit + OkHttp)                        │
│  └── System Providers (Call/SMS)                           │
├─────────────────────────────────────────────────────────────┤
│  Background Services                                       │
│  ├── MonitoringService (Foreground)                        │
│  ├── DataSyncService (WorkManager)                         │
│  └── Broadcast Receivers                                   │
└─────────────────────────────────────────────────────────────┘
```

## 🔒 Security & Privacy Features

### Data Protection
- **End-to-End Encryption**: All sensitive data encrypted before storage/transmission
- **SQLCipher Database**: Local database encrypted with AES-256
- **No SMS Content**: Only metadata (sender, timestamp, count) stored
- **Hashed Phone Numbers**: Phone numbers hashed for privacy
- **Secure Storage**: Android Keystore for encryption keys

### Privacy Controls
- **Granular Consent**: Users control what data is monitored
- **Family Permissions**: Seniors control what family members can see
- **Data Retention**: Automatic cleanup of old data
- **Audit Logging**: All access tracked and logged

### Security Hardening
- **Certificate Pinning**: API communication secured
- **Root Detection**: App detects compromised devices
- **Anti-Tampering**: Code obfuscation and integrity checks
- **Secure Communication**: TLS 1.3 for all network traffic

## 📱 Key Features

### For Seniors
- **Simple Interface**: Large buttons, clear text, senior-friendly design
- **Privacy Dashboard**: See what data is being monitored
- **Family Management**: Add/remove family members and set permissions
- **Emergency Contacts**: Quick access to important contacts
- **Monitoring Status**: Clear indication of monitoring status

### Background Monitoring
- **Call Log Monitoring**: Tracks incoming/outgoing/missed calls
- **SMS Monitoring**: Monitors SMS frequency and patterns (no content)
- **Contact Recognition**: Identifies known vs unknown contacts
- **Pattern Analysis**: Detects unusual activity patterns
- **Real-time Alerts**: Immediate family notifications for suspicious activity

## 🛠️ Technical Implementation

### Core Technologies
- **Language**: Kotlin 100%
- **Architecture**: MVVM with Repository pattern
- **Database**: Room + SQLCipher for encryption
- **Networking**: Retrofit + OkHttp with security interceptors
- **Background Work**: WorkManager + Foreground Services
- **Dependency Injection**: Manual DI (lightweight approach)

### Key Components

#### 1. Monitoring Service
```kotlin
class MonitoringService : LifecycleService() {
    // Foreground service for continuous monitoring
    // Processes call logs and SMS in real-time
    // Encrypts data before storage
}
```

#### 2. Data Encryption
```kotlin
class EncryptionManager {
    // AES-256 encryption for sensitive data
    // Android Keystore integration
    // Secure key generation and storage
}
```

#### 3. Permission Manager
```kotlin
class PermissionManager {
    // Runtime permission handling
    // Graceful degradation for denied permissions
    // User education about permission needs
}
```

#### 4. Family Connection
```kotlin
class FamilyConnectionManager {
    // Secure family member invitation system
    // Permission-based data sharing
    // Real-time notification system
}
```

## 🚀 Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 26+ (Android 8.0+)
- Kotlin 1.9+
- Gradle 8.0+

### Development Setup
1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd catamaran/android-app
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the android-app directory

3. **Configure API endpoints**
   - Copy `app/src/main/res/values/config.xml.example` to `config.xml`
   - Update API endpoints and keys

4. **Build and run**
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

### Production Build
```bash
# Generate signed APK
./gradlew assembleRelease

# Generate App Bundle (recommended)
./gradlew bundleRelease
```

## 📋 Permissions Explained

### Required Permissions
- **READ_PHONE_STATE**: Monitor call state changes
- **READ_CALL_LOG**: Access call history for analysis
- **READ_SMS**: Monitor SMS patterns (metadata only)
- **READ_CONTACTS**: Identify known contacts
- **FOREGROUND_SERVICE**: Background monitoring service
- **INTERNET**: Secure communication with backend

### Optional Permissions
- **POST_NOTIFICATIONS**: Family alert notifications
- **RECEIVE_BOOT_COMPLETED**: Auto-start monitoring after reboot
- **WAKE_LOCK**: Ensure monitoring continues during sleep

## 🔧 Configuration

### Debug Configuration
```kotlin
// app/build.gradle
buildTypes {
    debug {
        buildConfigField "String", "API_BASE_URL", '"http://10.0.2.2:3000"'
        buildConfigField "boolean", "DEBUG_MODE", "true"
    }
}
```

### Release Configuration
```kotlin
// app/build.gradle
buildTypes {
    release {
        buildConfigField "String", "API_BASE_URL", '"https://api.catamaran.app"'
        buildConfigField "boolean", "DEBUG_MODE", "false"
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
    }
}
```

## 🧪 Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumentation Tests
```bash
./gradlew connectedAndroidTest
```

### Security Testing
- Static analysis with Android Lint
- Dynamic analysis with OWASP ZAP
- Penetration testing for sensitive features

## 📊 Monitoring & Analytics

### Performance Monitoring
- Battery usage optimization
- Memory leak detection
- Network usage tracking
- Background service efficiency

### Privacy Compliance
- GDPR compliance checks
- Data retention policy enforcement
- User consent tracking
- Audit log generation

## 🚨 Security Considerations

### Threat Model
- **Malicious Apps**: Protection against data theft
- **Network Attacks**: Secure communication protocols
- **Device Compromise**: Root detection and response
- **Social Engineering**: User education and warnings

### Security Measures
- Certificate pinning for API calls
- Code obfuscation in release builds
- Runtime Application Self-Protection (RASP)
- Secure data wiping on uninstall

## 📞 Support & Troubleshooting

### Common Issues
1. **Permissions Denied**: Guide users through permission granting
2. **Battery Optimization**: Whitelist app from battery optimization
3. **Background Restrictions**: Handle manufacturer-specific limitations
4. **Network Issues**: Graceful offline handling

### Debug Information
- Enable debug logging in development builds
- Crash reporting with privacy-safe data
- Performance metrics collection
- User feedback integration

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🤝 Contributing

Please read our contributing guidelines and ensure all privacy and security practices are followed when contributing to this project. 