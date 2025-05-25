# 🛡️ Catamaran Family Safety Monitoring System

A comprehensive family safety platform that helps protect seniors from phone scams and enables real-time monitoring by family members. Built with privacy-first principles and enterprise-grade security.

## 🏗️ Architecture

```
catamaran/
├── android-app/          # Android monitoring app for seniors
├── backend/             # Node.js/Express API server  
├── backend-api/         # Alternative Node.js implementation
└── catamaran/
    └── web-dashboard/   # React family dashboard
```

## 🚀 Features

### 📱 **Android App** (Seniors)
- **Background Services**: Continuous call and SMS monitoring
- **Privacy Protection**: Local data encryption, hashed phone numbers
- **Battery Optimization**: Smart sync strategies, power management
- **Network Awareness**: WiFi/cellular adaptive behavior
- **Security**: AES-256 encryption, secure API communication

### 🖥️ **Web Dashboard** (Family Members)  
- **Real-time Monitoring**: Live activity feed and alerts
- **Risk Analysis**: AI-powered scam detection
- **Family Management**: Connect multiple seniors
- **Statistics**: Call patterns, SMS analysis, trend reports
- **Modern UI**: Glassmorphism design, responsive layout

### 🔧 **Backend API**
- **Authentication**: JWT with refresh tokens
- **Database**: PostgreSQL with Prisma ORM
- **Security**: Rate limiting, CORS, input validation
- **Privacy**: Encrypted data storage, GDPR compliant
- **Monitoring**: Comprehensive logging, health checks

## 🛠️ Quick Start

### Prerequisites
- Node.js 18+
- Android Studio (for mobile app)
- PostgreSQL 12+ (for backend)

### 1. Backend Setup
```bash
cd backend
npm install
cp env.example .env  # Configure your database and secrets
npm run prisma:migrate
npm run dev
```
Server starts at `http://localhost:3001`

### 2. Web Dashboard Setup  
```bash
cd catamaran/web-dashboard
npm install
npm start
```
Dashboard opens at `http://localhost:3000`

### 3. Android App Setup
```bash
cd android-app
# Open in Android Studio
# Build and install on senior's device
```

## 📡 API Endpoints

### Authentication
- `POST /api/auth/register` - Register user (SENIOR/FAMILY_MEMBER)
- `POST /api/auth/login` - User login
- `GET /api/auth/me` - Get current user

### Family Management  
- `POST /api/family/connect` - Link senior to family member
- `GET /api/family/connections` - Get family connections
- `GET /api/family/seniors` - Get seniors for family dashboard

### Activity Monitoring
- `POST /api/activity/sync` - Android uploads call/SMS data
- `GET /api/activity/:familyId` - Get activity for dashboard
- `GET /api/activity/alerts/:familyId` - Get high-risk alerts

## 🔐 Security & Privacy

### Data Protection
- **Phone Numbers**: SHA-256 hashed before storage
- **Contact Names**: AES-256 encrypted with user keys
- **SMS Content**: Never stored (metadata only)
- **Location**: Optional, encrypted if enabled

### Security Features
- JWT authentication with refresh tokens
- Rate limiting (100 req/15min per IP)
- Input validation on all endpoints  
- SQL injection protection via Prisma
- CORS and security headers
- Password hashing with bcrypt

### Privacy Compliance
- GDPR compliant data handling
- User consent for all monitoring
- Data retention policies
- Right to deletion
- Transparent data usage

## 🤖 Risk Analysis

The system uses intelligent algorithms to detect suspicious activity:

### Call Risk Factors
- Unknown/new phone numbers (+0.3)
- Very short calls <10 seconds (+0.4)  
- Off-hours calls (before 7AM/after 9PM) (+0.3)
- Long calls from unknown numbers (+0.5)
- Frequent missed calls from same number (+0.2)

### SMS Risk Factors  
- Messages from unknown senders (+0.4)
- Links in messages (+0.5)
- Off-hours messages (+0.2)
- High message frequency (+0.3)
- Suspicious keywords/patterns (+0.6)

Risk scores range from 0.0 (safe) to 1.0 (high risk).

## 🏃‍♂️ Development Workflow

### Running the Full Stack
```bash
# Terminal 1: Backend API
cd backend && npm run dev

# Terminal 2: Web Dashboard  
cd catamaran/web-dashboard && npm start

# Terminal 3: Database (optional)
cd backend && npm run prisma:studio
```

### Android Development
1. Open `android-app/` in Android Studio
2. Connect test device or start emulator
3. Build and run the app
4. Configure backend URL in app settings

## 📊 Database Schema

### Core Tables
- **users**: User accounts (seniors & family members)
- **family_connections**: Senior↔Family relationships  
- **call_logs**: Phone call metadata with risk scores
- **sms_logs**: SMS metadata with risk analysis
- **refresh_tokens**: JWT refresh token management

### Relationships
- Users can be SENIOR or FAMILY_MEMBER role
- Family connections link seniors to multiple family members
- Activity logs belong to senior users
- Risk scores calculated server-side for privacy

## 🚀 Deployment

### Backend (Production)
```bash
cd backend
npm run build
npm run prisma:deploy
npm start
```

### Frontend (Production)
```bash  
cd catamaran/web-dashboard
npm run build
# Serve build/ folder with nginx/apache
```

### Environment Variables
- Generate strong JWT secrets (32+ characters)
- Use production PostgreSQL database
- Configure CORS for your domains
- Set up SSL certificates
- Enable proper logging

## 🧪 Testing

### API Health Check
```bash
curl http://localhost:3001/health
```

### User Registration
```bash
curl -X POST http://localhost:3001/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"TestPassword123","role":"FAMILY_MEMBER"}'
```

### Activity Sync (Android)
```bash
curl -X POST http://localhost:3001/api/activity/sync \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"callLogs":[...],"smsLogs":[...]}'
```

## 📱 Mobile App Features

### Background Services
- **BackgroundMonitorService**: 24/7 activity monitoring
- **DataSyncService**: Intelligent data synchronization  
- **CallLogWatcher**: Real-time call detection
- **SmsWatcher**: SMS monitoring with privacy protection
- **BatteryOptimizationManager**: Power efficiency
- **NetworkUtils**: Adaptive connectivity handling

### Privacy Controls
- Local data encryption before transmission
- Configurable sync frequency
- Selective monitoring (calls only, SMS only, both)
- Easy disable/enable toggles
- Transparent data usage display

## 🔧 Configuration

### Android App Settings
- Backend server URL
- Sync frequency (WiFi: 5min, Cellular: 30min)
- Battery optimization exclusion
- Notification preferences
- Privacy settings

### Dashboard Settings
- Family member management
- Alert thresholds
- Notification preferences  
- Data retention period
- Export options

## 📈 Monitoring & Analytics

### Family Dashboard
- Real-time activity feed
- Risk alert notifications
- Call/SMS statistics
- Weekly/monthly reports
- Senior status indicators

### System Monitoring
- API health checks
- Database performance
- Background service status
- Sync success rates
- Error rate tracking

## 🤝 Contributing

1. **Fork the repository**
2. **Create feature branch**: `git checkout -b feature/amazing-feature`
3. **Follow coding standards**: TypeScript strict mode, ESLint
4. **Add tests**: Unit tests for utilities, integration tests for APIs
5. **Update documentation**: README, API docs, code comments
6. **Submit pull request**: Detailed description of changes

### Development Guidelines
- Privacy-first approach in all features
- Security review for new endpoints
- Performance testing for Android services
- Accessibility compliance for web dashboard
- Comprehensive error handling

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Issues**: GitHub Issues for bug reports
- **Discussions**: GitHub Discussions for questions
- **Security**: Report vulnerabilities privately via email
- **Documentation**: Wiki for detailed guides

---

**🛡️ Protecting families through technology, respecting privacy through design.**

*Built for the safety of our loved ones, with the highest standards of privacy and security.* 