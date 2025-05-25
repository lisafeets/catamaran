import winston from 'winston';
import path from 'path';

// Define log levels
const levels = {
  error: 0,
  warn: 1,
  info: 2,
  http: 3,
  debug: 4,
};

// Define colors for each level
const colors = {
  error: 'red',
  warn: 'yellow',
  info: 'green',
  http: 'magenta',
  debug: 'white',
};

// Add colors to winston
winston.addColors(colors);

// Define log format
const format = winston.format.combine(
  winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss:ms' }),
  winston.format.colorize({ all: true }),
  winston.format.printf(
    (info) => `${info.timestamp} ${info.level}: ${info.message}`,
  ),
);

// Define which transports the logger must use
const transports = [
  // Console transport
  new winston.transports.Console({
    level: process.env.LOG_LEVEL || 'info',
    format: winston.format.combine(
      winston.format.colorize(),
      winston.format.simple()
    )
  }),
  
  // File transport for errors
  new winston.transports.File({
    filename: path.join('logs', 'error.log'),
    level: 'error',
    format: winston.format.combine(
      winston.format.timestamp(),
      winston.format.json()
    )
  }),
  
  // File transport for all logs
  new winston.transports.File({
    filename: path.join('logs', 'combined.log'),
    format: winston.format.combine(
      winston.format.timestamp(),
      winston.format.json()
    )
  }),
];

// Create the logger
const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info',
  levels,
  format,
  transports,
  exceptionHandlers: [
    new winston.transports.File({ filename: path.join('logs', 'exceptions.log') })
  ],
  rejectionHandlers: [
    new winston.transports.File({ filename: path.join('logs', 'rejections.log') })
  ],
  exitOnError: false,
});

// Security-focused logging methods
export const securityLogger = {
  // Log authentication events
  authEvent: (event: string, userId?: string, ip?: string, details?: any) => {
    logger.info('AUTH_EVENT', {
      event,
      userId,
      ip,
      details: details ? JSON.stringify(details) : undefined,
      timestamp: new Date().toISOString(),
    });
  },

  // Log data access events
  dataAccess: (action: string, userId: string, resource: string, ip?: string) => {
    logger.info('DATA_ACCESS', {
      action,
      userId,
      resource,
      ip,
      timestamp: new Date().toISOString(),
    });
  },

  // Log security violations
  securityViolation: (violation: string, userId?: string, ip?: string, details?: any) => {
    logger.warn('SECURITY_VIOLATION', {
      violation,
      userId,
      ip,
      details: details ? JSON.stringify(details) : undefined,
      timestamp: new Date().toISOString(),
    });
  },

  // Log privacy events (GDPR compliance)
  privacyEvent: (event: string, userId: string, dataType: string, details?: any) => {
    logger.info('PRIVACY_EVENT', {
      event,
      userId,
      dataType,
      details: details ? JSON.stringify(details) : undefined,
      timestamp: new Date().toISOString(),
    });
  },
};

export { logger }; 