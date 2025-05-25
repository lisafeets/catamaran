# Catamaran Architecture & Design Decisions

## 🏗️ System Overview

Catamaran is a privacy-first family safety application designed to help adult children monitor their seniors' phone activity for potential scams and emergencies. The system is built with security, privacy, and scalability as core principles.

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Android App   │    │   Backend API    │    │  Web Dashboard  │
│  (Senior User)  │◄──►│   Node.js +      │◄──►│ (Family Views)  │
│     Kotlin      │    │   PostgreSQL     │    │ React + TypeScript│
└─────────────────┘    └──────────────────┘    └─────────────────┘
        │                        │                        │
        │                        │                        │
        ▼                        ▼                        ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ Local Database  │    │   PostgreSQL     │    │   Browser       │
│ Room+SQLCipher  │    │   + Prisma ORM   │    │   Storage       │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## 🔒 Security Architecture

### 1. Data Flow Security

```
Senior's Phone Activity → Local Encryption → Secure Transmission → Backend Processing → Family Notifications
```

**Key Security Layers:**
- **Device Level**: SQLCipher encryption, Android Keystore
- **Network Level**: TLS 1.3, certificate pinning, request signing
- **Application Level**: JWT tokens, role-based access control
- **Database Level**: Encrypted fields, audit logging

### 2. Privacy-by-Design Principles

#### Data Minimization
- **SMS Content**: Never stored or transmitted
- **Call Content**: Never recorded or accessed
- **Phone Numbers**: Hashed using SHA-256 + salt
- **Contact Names**: Encrypted with AES-256

#### Purpose Limitation
- Data only used for scam detection and family safety
- No advertising, analytics, or third-party sharing
- Automatic data deletion after retention period

#### Consent Management
- Granular permissions for each data type
- Family members require explicit senior approval
- Easy opt-out and data deletion

## 🏛️ Backend Architecture

### Technology Stack Rationale

#### Node.js + Express
**Why chosen:**
- Excellent real-time capabilities with WebSockets
- Rich ecosystem for security libraries
- Fast development and deployment
- Strong TypeScript support

**Security configurations:**
- Helmet.js for security headers
- Rate limiting to prevent abuse
- CORS restrictions
- Input validation with Zod

#### PostgreSQL + Prisma
**Why chosen:**
- ACID compliance for sensitive data
- Advanced encryption capabilities
- Excellent audit logging support
- Type-safe database operations with Prisma

**Security features:**
- Row-level security (RLS)
- Encrypted columns for sensitive data
- Comprehensive audit trails
- Backup encryption

### API Design Principles

#### RESTful with Security Focus
```typescript
// Example secure endpoint
POST /api/logs/calls
Authorization: Bearer <jwt-token>
Content-Type: application/json
X-Request-Signature: <hmac-signature>

{
  "encryptedData": "...",
  "timestamp": "2024-01-15T10:30:00Z",
  "deviceId": "hashed-device-id"
}
```

#### Real-time Notifications
- WebSocket connections for family alerts
- Server-sent events for dashboard updates
- Push notifications for mobile alerts

## 📱 Android App Architecture

### MVVM + Repository Pattern

```kotlin
// Architecture layers
UI Layer (Activities/Fragments)
    ↓
ViewModel Layer (Business Logic)
    ↓
Repository Layer (Data Abstraction)
    ↓
Data Sources (Local DB, Remote API, System Providers)
```

### Security Implementation

#### Local Data Protection
```kotlin
// Encrypted database with SQLCipher
@Database(
    entities = [CallLog::class, SmsLog::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CatamaranDatabase : RoomDatabase() {
    // Database encrypted with user-specific key
}
```

#### Background Monitoring
```kotlin
// Foreground service for continuous monitoring
class MonitoringService : LifecycleService() {
    // Processes call logs and SMS metadata
    // Encrypts data before storage
    // Sends encrypted data to backend
}
```

### Privacy Controls
- Users can disable specific monitoring features
- Data retention settings (30, 60, 90 days)
- Family permission management
- Emergency override capabilities

## 🌐 Web Dashboard Architecture

### React + TypeScript Stack

#### State Management Strategy
```typescript
// Zustand for lightweight state management
interface AppState {
  user: User | null;
  familyMembers: FamilyMember[];
  alerts: Alert[];
  callLogs: CallLog[];
  smsLogs: SmsLog[];
}
```

#### Data Fetching with TanStack Query
```typescript
// Efficient data fetching with caching
const { data: alerts } = useQuery({
  queryKey: ['alerts', familyMemberId],
  queryFn: () => fetchAlerts(familyMemberId),
  refetchInterval: 30000, // Real-time updates
});
```

### Security Features
- JWT token management
- Automatic session timeout
- CSRF protection
- Content Security Policy (CSP)

## 🗄️ Database Schema Design

### Core Entities

#### Users & Relationships
```sql
-- Users table with role-based access
users (
  id, email, password_hash, role, 
  consent_given_at, privacy_policy_version
)

-- Family connections with permissions
family_connections (
  senior_id, guardian_id, status,
  relationship_type, permissions
)
```

#### Activity Logs (Privacy-Focused)
```sql
-- Call logs (no content, metadata only)
call_logs (
  id, user_id, phone_number_hash,
  contact_name_encrypted, duration,
  call_type, timestamp, risk_score
)

-- SMS logs (no content, metadata only)
sms_logs (
  id, user_id, phone_number_hash,
  contact_name_encrypted, message_count,
  sms_type, timestamp, risk_score
)
```

#### Audit & Compliance
```sql
-- Comprehensive audit logging
audit_logs (
  id, user_id, action, entity, entity_id,
  ip_address, user_agent, timestamp,
  old_values_encrypted, new_values_encrypted
)
```

## 🚨 Threat Model & Mitigations

### Identified Threats

#### 1. Data Breach
**Threat**: Unauthorized access to sensitive data
**Mitigation**: 
- End-to-end encryption
- Zero-knowledge architecture
- Regular security audits

#### 2. Man-in-the-Middle Attacks
**Threat**: Interception of data transmission
**Mitigation**:
- Certificate pinning
- TLS 1.3 encryption
- Request signing

#### 3. Device Compromise
**Threat**: Malware or root access on senior's device
**Mitigation**:
- Root detection
- App integrity checks
- Secure key storage

#### 4. Social Engineering
**Threat**: Attackers impersonating family members
**Mitigation**:
- Multi-factor authentication
- Out-of-band verification
- Suspicious activity alerts

## 📊 Scalability Considerations

### Horizontal Scaling Strategy

#### Backend Services
- Stateless API design for load balancing
- Database read replicas for query performance
- Redis for session management and caching
- Message queues for background processing

#### Data Storage
- Partitioned tables by user/date
- Automated data archiving
- CDN for static assets
- Backup and disaster recovery

### Performance Optimization

#### Android App
- Background service optimization
- Battery usage minimization
- Efficient data synchronization
- Local caching strategies

#### Web Dashboard
- Code splitting and lazy loading
- Service worker for offline capability
- Optimized bundle sizes
- Progressive Web App (PWA) features

## 🔧 Development & Deployment

### Development Workflow
1. **Local Development**: Docker containers for consistent environments
2. **Testing**: Automated unit, integration, and security tests
3. **Staging**: Production-like environment for final testing
4. **Production**: Blue-green deployment with rollback capability

### Security Testing
- Static Application Security Testing (SAST)
- Dynamic Application Security Testing (DAST)
- Dependency vulnerability scanning
- Penetration testing for critical features

### Monitoring & Observability
- Application performance monitoring
- Security event logging
- Privacy compliance tracking
- User experience analytics (privacy-safe)

## 🌟 Key Architectural Decisions

### 1. Privacy-First Design
**Decision**: Never store SMS content or call recordings
**Rationale**: Minimizes privacy risk and regulatory compliance burden
**Trade-off**: Limited content-based scam detection capabilities

### 2. Hybrid Encryption Approach
**Decision**: Client-side encryption with server-side key management
**Rationale**: Balances security with operational requirements
**Trade-off**: Increased complexity but better security posture

### 3. Monorepo Structure
**Decision**: Separate repositories for each component
**Rationale**: Independent deployment and team ownership
**Trade-off**: More complex dependency management

### 4. Real-time Architecture
**Decision**: WebSockets for family notifications
**Rationale**: Immediate alerts for emergency situations
**Trade-off**: Increased server resource usage

## 🚀 Future Enhancements

### Planned Features
- Machine learning for scam pattern detection
- Integration with emergency services
- Multi-language support
- Wearable device integration

### Technical Improvements
- GraphQL API for more efficient data fetching
- Microservices architecture for better scalability
- Advanced analytics with privacy preservation
- Blockchain for audit trail immutability

## 📋 Compliance & Regulations

### Privacy Regulations
- **GDPR**: Right to be forgotten, data portability
- **CCPA**: California Consumer Privacy Act compliance
- **HIPAA**: Health information protection (if applicable)

### Security Standards
- **OWASP**: Top 10 security vulnerabilities addressed
- **SOC 2**: Security and availability controls
- **ISO 27001**: Information security management

This architecture provides a solid foundation for a secure, scalable, and privacy-focused family safety application while maintaining the flexibility to evolve with changing requirements and threats. 