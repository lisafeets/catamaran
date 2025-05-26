const jwt = require('jsonwebtoken');

// Default JWT secret from the backend (for testing only)
const jwtSecret = 'catamaran-default-jwt-secret-for-testing-only-change-in-production';

// Create a test user payload
const payload = {
  id: 'test-user-123',
  email: 'test@example.com',
  role: 'SENIOR'
};

// Generate token (expires in 24 hours)
const token = jwt.sign(payload, jwtSecret, { expiresIn: '24h' });

console.log('Test JWT Token:');
console.log(token);
console.log('\nUse this token in Authorization header as:');
console.log(`Authorization: Bearer ${token}`); 