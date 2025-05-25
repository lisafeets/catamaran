# 🛡️ Catamaran Backend API

A secure, production-ready backend API for the Catamaran Family Safety Monitoring System. Built with Express.js, TypeScript, PostgreSQL, and Prisma.

## 🚀 Features

- **Authentication & Authorization**: JWT-based auth with refresh tokens
- **Family Connections**: Link seniors with family members
- **Activity Monitoring**: Phone call and SMS tracking with risk analysis
- **Real-time Sync**: Android app data synchronization
- **Privacy-First**: Phone numbers hashed, contact names encrypted
- **Security**: Rate limiting, CORS, Helmet, input validation
- **Logging**: Comprehensive Winston logging
- **Database**: PostgreSQL with Prisma ORM

## 📋 Prerequisites

- Node.js 18+ 
- PostgreSQL 12+
- npm or yarn

## 🛠️ Installation

1. **Clone and navigate to backend**:
   ```bash
   cd backend
   ```

2. **Install dependencies**:
   ```bash
   npm install
   ```

3. **Environment setup**:
   ```bash
   cp env.example .env
   # Edit .env with your actual values
   ```

4. **Set up PostgreSQL database**:
   ```bash
   # Create database
   createdb catamaran
   
   # Run migrations
   npm run prisma:migrate
   
   # Generate Prisma client
   npm run prisma:generate
   ```

## ⚙️ Environment Variables

Copy `env.example` to `.env` and configure:

```env
# Database
DATABASE_URL="postgresql://username:password@localhost:5432/catamaran"

# Server
PORT=3001
NODE_ENV=development

# JWT Secrets (Generate strong secrets for production!)
JWT_SECRET="your-super-secure-jwt-secret-key-here-min-32-chars"
JWT_REFRESH_SECRET="your-super-secure-refresh-secret-key-here-min-32-chars"
JWT_ACCESS_EXPIRY="15m"
JWT_REFRESH_EXPIRY="7d"

# CORS
CORS_ORIGIN="http://localhost:3000,http://localhost:3001"

# Rate Limiting
RATE_LIMIT_WINDOW_MS=900000
RATE_LIMIT_MAX_REQUESTS=100

# Security
BCRYPT_ROUNDS=12

# Logging
LOG_LEVEL=info
```

## 🚀 Running the Server

### Development
```bash
npm run dev
```

### Production
```bash
npm run build
npm start
```

### Database Operations
```bash
# Run migrations
npm run prisma:migrate

# Generate Prisma client
npm run prisma:generate

# Open Prisma Studio
npm run prisma:studio

# Deploy migrations (production)
npm run prisma:deploy
```

## 📡 API Endpoints

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login
- `POST /api/auth/refresh` - Refresh access token
- `POST /api/auth/logout` - User logout
- `GET /api/auth/me` - Get current user info

### Family Management
- `POST /api/family/connect` - Connect family member to senior
- `GET /api/family/connections` - Get user's family connections
- `GET /api/family/seniors` - Get seniors for family member
- `DELETE /api/family/disconnect/:connectionId` - Remove family connection
- `PUT /api/family/relationship/:connectionId` - Update relationship type

### Activity Monitoring
- `POST /api/activity/sync` - Android app uploads activity data
- `GET /api/activity/:familyId` - Get dashboard data for family member
- `GET /api/activity/alerts/:familyId` - Get high-risk alerts

### System
- `GET /health` - Health check endpoint

## 🔐 Authentication

The API uses JWT tokens with refresh token rotation:

1. **Register/Login** returns access token (15min) + refresh token (7d)
2. **Access Token** required for protected endpoints via `Authorization: Bearer <token>`
3. **Refresh Token** used to get new access tokens
4. **Logout** revokes refresh tokens

Example request:
```bash
curl -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
     http://localhost:3001/api/auth/me
```

## 📊 Database Schema

### Users
- **id**: Unique identifier
- **email**: User email (unique)
- **role**: SENIOR | FAMILY_MEMBER | ADMIN
- **password**: Hashed password
- **isActive**: Account status

### Family Connections
- **seniorId**: Senior user ID
- **familyId**: Family member user ID
- **relationship**: CHILD | SPOUSE | PARENT | SIBLING | CAREGIVER | OTHER
- **isActive**: Connection status

### Call Logs
- **seniorId**: Senior who made/received call
- **phoneNumber**: Hashed phone number
- **contactName**: Encrypted contact name
- **duration**: Call duration in seconds
- **callType**: incoming | outgoing | missed
- **riskScore**: Calculated risk (0.0-1.0)

### SMS Logs
- **seniorId**: Senior who sent/received SMS
- **senderNumber**: Hashed phone number
- **contactName**: Encrypted contact name
- **messageType**: received | sent
- **hasLink**: Contains links (boolean)
- **riskScore**: Calculated risk (0.0-1.0)

## 🛡️ Security Features

- **Rate Limiting**: 100 requests per 15 minutes per IP
- **CORS**: Configurable origin restrictions
- **Helmet**: Security headers
- **Input Validation**: Express-validator on all endpoints
- **SQL Injection Protection**: Prisma ORM parameterized queries
- **Password Hashing**: bcrypt with configurable rounds
- **JWT Security**: Access + refresh token pattern
- **Privacy**: Phone numbers hashed, contact names encrypted

## 🔍 Risk Analysis

The system calculates risk scores (0.0-1.0) for calls and SMS:

### Call Risk Factors:
- Unknown contact (+0.3)
- Very short calls <10s (+0.4)
- Long calls from unknown numbers (+0.5)
- Off-hours calls 7AM-9PM (+0.3)
- Missed calls from unknown numbers (+0.2)

### SMS Risk Factors:
- Unknown sender (+0.4)
- Contains links (+0.5)
- Off-hours messages (+0.2)
- High message count (+0.3)

## 📝 Logging

Winston logging with multiple transports:

- **Console**: Development logging with colors
- **Files**: `logs/error.log` and `logs/combined.log`
- **Rotation**: 5MB max, 5 files retained
- **Levels**: error, warn, info, debug

## 🚀 Deployment

### Environment Setup
1. Set `NODE_ENV=production`
2. Use strong, unique JWT secrets (32+ characters)
3. Configure production database
4. Set appropriate CORS origins
5. Configure rate limiting for production load

### Database Migration
```bash
npm run prisma:deploy
```

### Process Management
Use PM2 or similar for production:
```bash
pm2 start dist/server.js --name catamaran-api
```

## 🧪 Testing

Health check endpoint:
```bash
curl http://localhost:3001/health
```

Expected response:
```json
{
  "status": "healthy",
  "timestamp": "2024-01-15T10:30:00.000Z",
  "version": "1.0.0",
  "environment": "development"
}
```

## 📚 Error Handling

The API returns consistent error responses:

```json
{
  "success": false,
  "error": {
    "message": "Validation failed",
    "details": { /* Additional error details in development */ }
  },
  "timestamp": "2024-01-15T10:30:00.000Z",
  "path": "/api/auth/login"
}
```

Common HTTP status codes:
- `200` - Success
- `201` - Created
- `400` - Bad Request / Validation Error
- `401` - Unauthorized
- `403` - Forbidden
- `404` - Not Found
- `409` - Conflict (duplicate)
- `429` - Too Many Requests
- `500` - Internal Server Error

## 🤝 Contributing

1. Follow TypeScript strict mode
2. Add input validation for all endpoints
3. Include proper error handling
4. Add logging for important operations
5. Update this README for new features

## 📄 License

MIT License - see LICENSE file for details.

---

**🛡️ Built for family safety and privacy protection** 