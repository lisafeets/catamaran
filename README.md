# Catamaran - Family Safety & Elder Protection

Catamaran is a privacy-focused family safety application that helps adult children monitor their seniors' phone activity for potential scams and emergencies. The app provides non-intrusive monitoring with robust privacy controls and real-time family notifications.

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Android App   │    │   Backend API    │    │  Web Dashboard  │
│  (Senior User)  │◄──►│   Node.js +      │◄──►│ (Family Views)  │
│     Kotlin      │    │   PostgreSQL     │    │ React + TypeScript│
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### Components

1. **Android App** (`/android-app`)
   - Native Kotlin application for seniors
   - Background monitoring of calls and SMS
   - Local Room database for offline functionality
   - End-to-end encryption before data transmission
   - Simple, senior-friendly UI

2. **Backend API** (`/backend-api`)
   - Node.js + Express REST API
   - PostgreSQL with Prisma ORM
   - JWT-based authentication
   - WebSocket support for real-time notifications
   - Data encryption and privacy controls

3. **Web Dashboard** (`/web-dashboard`)
   - React TypeScript application for family members
   - Real-time activity monitoring
   - Alert management and configuration
   - Responsive design with Tailwind CSS

## 🔒 Security & Privacy Features

- **End-to-End Encryption**: All sensitive data encrypted before transmission
- **No SMS Content Storage**: Only metadata (sender, count, timestamp) stored
- **Granular Permissions**: Family members can only see authorized data
- **Audit Logging**: All access and changes tracked
- **Data Minimization**: Only essential data collected and stored
- **Local Processing**: Most analysis done on-device when possible

## 🚀 Quick Start

### Prerequisites
- Android Studio (for Android development)
- Node.js 18+ (for backend)
- PostgreSQL 14+ (for database)

### Setup
```bash
# Clone and setup
git clone <repository-url>
cd catamaran

# Setup backend
cd backend-api
npm install
cp .env.example .env
npm run db:migrate
npm run dev

# Setup web dashboard
cd ../web-dashboard
npm install
npm run dev

# Setup Android app
# Open android-app folder in Android Studio
```

## 📱 App Features

### For Seniors (Android App)
- Simple setup with family member invitation
- Non-intrusive background monitoring
- Emergency contact quick access
- Privacy controls and consent management

### For Family Members (Web Dashboard)
- Real-time activity overview
- Scam detection alerts
- Call and SMS pattern analysis
- Emergency notifications

## 🛠️ Development

Each component has its own development environment:

- **Android**: Standard Android development with Gradle
- **Backend**: Node.js with hot reloading
- **Frontend**: React with Vite for fast development

See individual README files in each directory for detailed setup instructions.

## 📊 Database Schema

Core entities include:
- Users (seniors and family members)
- Family relationships with permission levels
- Call logs (encrypted metadata only)
- SMS logs (no content, metadata only)
- Alert configurations and notifications
- Audit logs for all activities

## 🤝 Contributing

Please read our contributing guidelines and ensure all privacy and security practices are followed when contributing to this project.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details. 