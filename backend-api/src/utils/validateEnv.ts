import { logger } from './logger';

interface EnvironmentConfig {
  // Database
  DATABASE_URL: string;
  
  // Server
  NODE_ENV: string;
  PORT: string;
  
  // JWT Authentication
  JWT_SECRET: string;
  JWT_REFRESH_SECRET: string;
  JWT_ACCESS_EXPIRY: string;
  JWT_REFRESH_EXPIRY: string;
  
  // Encryption
  ENCRYPTION_KEY: string;
  ENCRYPTION_ALGORITHM: string;
  
  // Rate Limiting
  RATE_LIMIT_WINDOW_MS: string;
  RATE_LIMIT_MAX_REQUESTS: string;
  
  // CORS
  CORS_ORIGIN: string;
  
  // Security
  BCRYPT_ROUNDS: string;
  API_KEY_SECRET: string;
}

const requiredEnvVars: (keyof EnvironmentConfig)[] = [
  'DATABASE_URL',
  'NODE_ENV',
  'PORT',
  'JWT_SECRET',
  'JWT_REFRESH_SECRET',
  'JWT_ACCESS_EXPIRY',
  'JWT_REFRESH_EXPIRY',
  'ENCRYPTION_KEY',
  'ENCRYPTION_ALGORITHM',
  'RATE_LIMIT_WINDOW_MS',
  'RATE_LIMIT_MAX_REQUESTS',
  'CORS_ORIGIN',
  'BCRYPT_ROUNDS',
  'API_KEY_SECRET'
];

export const validateEnv = (): void => {
  const missingVars: string[] = [];
  const invalidVars: string[] = [];

  // Check for missing required variables
  for (const varName of requiredEnvVars) {
    if (!process.env[varName]) {
      missingVars.push(varName);
    }
  }

  // Validate specific environment variables
  if (process.env.JWT_SECRET && process.env.JWT_SECRET.length < 32) {
    invalidVars.push('JWT_SECRET (must be at least 32 characters)');
  }

  if (process.env.ENCRYPTION_KEY && process.env.ENCRYPTION_KEY.length !== 32) {
    invalidVars.push('ENCRYPTION_KEY (must be exactly 32 characters)');
  }

  if (process.env.NODE_ENV && !['development', 'production', 'test'].includes(process.env.NODE_ENV)) {
    invalidVars.push('NODE_ENV (must be development, production, or test)');
  }

  if (process.env.PORT && (isNaN(Number(process.env.PORT)) || Number(process.env.PORT) < 1 || Number(process.env.PORT) > 65535)) {
    invalidVars.push('PORT (must be a valid port number 1-65535)');
  }

  if (process.env.BCRYPT_ROUNDS && (isNaN(Number(process.env.BCRYPT_ROUNDS)) || Number(process.env.BCRYPT_ROUNDS) < 10 || Number(process.env.BCRYPT_ROUNDS) > 15)) {
    invalidVars.push('BCRYPT_ROUNDS (must be a number between 10-15)');
  }

  // Report errors
  if (missingVars.length > 0) {
    logger.error('Missing required environment variables:', missingVars);
    throw new Error(`Missing required environment variables: ${missingVars.join(', ')}`);
  }

  if (invalidVars.length > 0) {
    logger.error('Invalid environment variables:', invalidVars);
    throw new Error(`Invalid environment variables: ${invalidVars.join(', ')}`);
  }

  // Log successful validation
  logger.info('Environment validation passed');
  logger.info(`Running in ${process.env.NODE_ENV} mode`);
};

// Export typed environment for use throughout the app
export const env = {
  // Database
  DATABASE_URL: process.env.DATABASE_URL!,
  
  // Server
  NODE_ENV: process.env.NODE_ENV as 'development' | 'production' | 'test',
  PORT: parseInt(process.env.PORT!, 10),
  HOST: process.env.HOST || 'localhost',
  
  // JWT
  JWT_SECRET: process.env.JWT_SECRET!,
  JWT_REFRESH_SECRET: process.env.JWT_REFRESH_SECRET!,
  JWT_ACCESS_EXPIRY: process.env.JWT_ACCESS_EXPIRY!,
  JWT_REFRESH_EXPIRY: process.env.JWT_REFRESH_EXPIRY!,
  
  // Encryption
  ENCRYPTION_KEY: process.env.ENCRYPTION_KEY!,
  ENCRYPTION_ALGORITHM: process.env.ENCRYPTION_ALGORITHM!,
  
  // Security
  BCRYPT_ROUNDS: parseInt(process.env.BCRYPT_ROUNDS!, 10),
  API_KEY_SECRET: process.env.API_KEY_SECRET!,
  
  // Rate Limiting
  RATE_LIMIT_WINDOW_MS: parseInt(process.env.RATE_LIMIT_WINDOW_MS!, 10),
  RATE_LIMIT_MAX_REQUESTS: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS!, 10),
  
  // CORS
  CORS_ORIGIN: process.env.CORS_ORIGIN!,
  
  // Optional configurations
  EMAIL_HOST: process.env.EMAIL_HOST,
  EMAIL_PORT: process.env.EMAIL_PORT ? parseInt(process.env.EMAIL_PORT, 10) : undefined,
  EMAIL_USER: process.env.EMAIL_USER,
  EMAIL_PASS: process.env.EMAIL_PASS,
  EMAIL_FROM: process.env.EMAIL_FROM,
  
  // Data retention
  DATA_RETENTION_DAYS: process.env.DATA_RETENTION_DAYS ? parseInt(process.env.DATA_RETENTION_DAYS, 10) : 30,
  AUDIT_LOG_RETENTION_DAYS: process.env.AUDIT_LOG_RETENTION_DAYS ? parseInt(process.env.AUDIT_LOG_RETENTION_DAYS, 10) : 90,
  
  // WebSocket
  WS_HEARTBEAT_INTERVAL: process.env.WS_HEARTBEAT_INTERVAL ? parseInt(process.env.WS_HEARTBEAT_INTERVAL, 10) : 30000,
  WS_CONNECTION_TIMEOUT: process.env.WS_CONNECTION_TIMEOUT ? parseInt(process.env.WS_CONNECTION_TIMEOUT, 10) : 60000,
}; 