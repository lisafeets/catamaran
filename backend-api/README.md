# Catamaran Backend API

A secure, privacy-first backend API for the Catamaran family safety application. This system processes phone activity data from seniors' devices and provides real-time alerts to family members while maintaining strict privacy and security standards.

## 🔒 Privacy & Security Features

- **End-to-end encryption** for all sensitive data (phone numbers, contact names)
- **Zero message content storage** - only metadata is processed
- **Hashed phone numbers** for privacy-preserving database indexing
- **JWT authentication** with refresh tokens
- **Rate limiting** and request validation
- **Comprehensive audit logging**
- **Data retention policies** (30-day automatic cleanup)
- **GDPR compliance** ready

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Mobile App    │    │   Web Dashboard │    │  Family Members │
│   (Senior)      │    │   (Family)      │    │   (Real-time)   │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          │ HTTPS/JWT            │ HTTPS/JWT            │ WebSocket
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │     Express.js API       │
                    │   (Authentication,       │
                    │    Rate Limiting,        │
                    │     Validation)          │
                    └─────────────┬─────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │     Business Logic       │
                    │  (Activity Analysis,     │
                    │   Risk Detection,        │
                    │   Notifications)         │
                    └─────────────┬─────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │   PostgreSQL Database    │
                    │    (Encrypted Data,      │
                    │     Audit Logs)          │
                    └─────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- Node.js 18+ 
- PostgreSQL 14+
- npm or yarn

### Installation

1. **Clone and install dependencies:**
```bash
cd backend-api
npm install
```

2. **Set up environment variables:**
```bash
cp .env.example .env
# Edit .env with your configuration
```

3. **Set up the database:**
```bash
# Generate Prisma client
npm run db:generate

# Run database migrations
npm run db:migrate

# Seed initial data (optional)
npm run db:seed
```

4. **Start the development server:**
```bash
npm run dev
```

The API will be available at `http://localhost:3000`

## 📋 Environment Configuration

Create a `.env` file with the following variables:

```env
# Database
DATABASE_URL="postgresql://username:password@localhost:5432/catamaran_db"

# Server
NODE_ENV=development
PORT=3000

# JWT Authentication (generate secure 32+ character keys)
JWT_SECRET="your-super-secure-jwt-secret-key-here-min-32-chars"
JWT_REFRESH_SECRET="your-super-secure-refresh-secret-key-here-min-32-chars"
JWT_ACCESS_EXPIRY="15m"
JWT_REFRESH_EXPIRY="7d"

# Encryption (exactly 32 characters for AES-256)
ENCRYPTION_KEY="your-32-char-encryption-key-here12"
ENCRYPTION_ALGORITHM="aes-256-gcm"

# Security
BCRYPT_ROUNDS=12
API_KEY_SECRET="your-api-key-for-mobile-app"

# Rate Limiting
RATE_LIMIT_WINDOW_MS=900000  # 15 minutes
RATE_LIMIT_MAX_REQUESTS=100

# CORS
CORS_ORIGIN="http://localhost:3001,http://localhost:3000"

# Data Retention
DATA_RETENTION_DAYS=30
AUDIT_LOG_RETENTION_DAYS=90
```

## 🔌 API Endpoints

### Authentication

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/auth/register` | Register new user (senior/family) | No |
| POST | `/api/auth/login` | User login | No |
| POST | `/api/auth/logout` | User logout | Yes |
| POST | `/api/auth/refresh` | Refresh access token | No |
| POST | `/api/auth/change-password` | Change password | Yes |
| POST | `/api/auth/forgot-password` | Request password reset | No |
| GET | `/api/auth/verify` | Verify session | Yes |

### Activity Logs

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/logs/calls` | Upload call logs from mobile | Yes |
| POST | `/api/logs/sms` | Upload SMS logs from mobile | Yes |
| GET | `/api/logs/summary` | Get activity summary for date range | Yes |
| GET | `/api/logs/today` | Get today's activity summary | Yes |
| GET | `/api/logs/health` | Health check for mobile app | Yes |

### Family Management

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/family/invite` | Invite family member | Yes |
| GET | `/api/family/connections` | Get family connections | Yes |
| PUT | `/api/family/connections/:id` | Update connection status | Yes |
| DELETE | `/api/family/connections/:id` | Remove family connection | Yes |

### Notifications & Alerts

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/alerts` | Get alerts for user | Yes |
| PUT | `/api/alerts/:id/read` | Mark alert as read | Yes |
| PUT | `/api/alerts/:id/acknowledge` | Acknowledge alert | Yes |

## 📊 Data Models

### User Registration
```json
{
  "email": "senior@example.com",
  "password": "SecurePass123",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+1234567890",
  "role": "SENIOR"
}
```

### Call Log Upload
```json
{
  "logs": [
    {
      "phoneNumber": "+1234567890",
      "contactName": "Doctor Smith",
      "duration": 180,
      "callType": "INCOMING",
      "timestamp": "2024-01-15T10:30:00Z",
      "isKnownContact": true
    }
  ]
}
```

### SMS Log Upload
```json
{
  "logs": [
    {
      "phoneNumber": "+1234567890",
      "contactName": "Bank Alert",
      "messageCount": 1,
      "smsType": "INCOMING",
      "timestamp": "2024-01-15T10:30:00Z",
      "isKnownContact": false
    }
  ]
}
```

## 🔐 Security Implementation

### Data Encryption
- **Phone numbers**: Hashed using HMAC-SHA256 for indexing
- **Contact names**: AES-256-GCM encryption
- **Sensitive metadata**: Encrypted JSON storage
- **Passwords**: bcrypt with 12 rounds

### Authentication Flow
1. User registers/logs in → receives JWT access token (15min) + refresh token (7 days)
2. Mobile app includes `Authorization: Bearer <token>` header
3. Token expires → use refresh token to get new access token
4. Logout → invalidate tokens and update user status

### Rate Limiting
- 100 requests per 15-minute window per IP
- Configurable via environment variables
- Returns 429 status with retry information

## 📱 Mobile App Integration

### Authentication
```javascript
// Login
const response = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password })
});

const { user, tokens } = await response.json();
// Store tokens securely
```

### Upload Activity Data
```javascript
// Upload call logs
const response = await fetch('/api/logs/calls', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${accessToken}`
  },
  body: JSON.stringify({ logs: callLogs })
});
```

### WebSocket Connection
```javascript
const ws = new WebSocket('ws://localhost:3000');

// Authenticate
ws.send(JSON.stringify({
  type: 'authenticate',
  data: { token: accessToken }
}));

// Receive real-time alerts
ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  if (message.type === 'alert') {
    // Handle family alert
    showNotification(message.data);
  }
};
```

## 🔍 Risk Analysis & Alerts

The system automatically analyzes activity patterns and generates alerts:

### Call Risk Factors
- Unknown callers (risk score +0.3)
- Very short calls (<10 seconds, risk score +0.4)
- Long calls from unknown numbers (>30 minutes, risk score +0.5)
- Off-hours calls (before 7AM or after 9PM, risk score +0.3)

### SMS Risk Factors
- Unknown senders (risk score +0.3)
- Multiple messages from same number (risk score +0.4)
- Off-hours messages (risk score +0.3)

### Alert Triggers
- **High Risk** (score > 0.7): Immediate family notification
- **Medium Risk** (score > 0.5): Daily summary inclusion
- **Frequent Unknown Activity**: 10+ unknown calls or 20+ unknown SMS in 24 hours

## 🚀 Deployment

### Production Environment

1. **Database Setup:**
```bash
# Production database migration
npm run db:deploy
```

2. **Environment Variables:**
```env
NODE_ENV=production
DATABASE_URL="postgresql://user:pass@prod-db:5432/catamaran"
# Use strong, unique keys in production
```

3. **Docker Deployment:**
```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
RUN npm run build
EXPOSE 3000
CMD ["npm", "start"]
```

4. **Health Checks:**
- `GET /health` - Basic server health
- `GET /api/logs/health` - Authenticated health check

### Monitoring & Logging

- **Winston logging** with configurable levels
- **Audit trail** for all user actions
- **Performance metrics** via middleware
- **Error tracking** with stack traces

## 🧪 Testing

```bash
# Run tests
npm test

# Run tests with coverage
npm run test:coverage

# Run tests in watch mode
npm run test:watch
```

## 📚 API Documentation

### Error Responses
All endpoints return consistent error formats:

```json
{
  "error": "Validation failed",
  "message": "Detailed error description",
  "details": [
    {
      "field": "email",
      "message": "Valid email is required"
    }
  ]
}
```

### Success Responses
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { /* response data */ }
}
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Commit changes: `git commit -am 'Add new feature'`
4. Push to branch: `git push origin feature/new-feature`
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🆘 Support

For support and questions:
- Email: support@catamaran.app
- Documentation: [docs.catamaran.app](https://docs.catamaran.app)
- Issues: [GitHub Issues](https://github.com/catamaran/backend-api/issues)

---

**Built with ❤️ for family safety and senior protection** 