import { Request, Response, NextFunction } from 'express';
import { logger } from '@/utils/logger';

interface CustomError extends Error {
  statusCode?: number;
  code?: string;
  isOperational?: boolean;
}

// Custom error classes
export class AppError extends Error {
  public statusCode: number;
  public isOperational: boolean;
  public code?: string;

  constructor(message: string, statusCode: number, code?: string) {
    super(message);
    this.statusCode = statusCode;
    this.isOperational = true;
    this.code = code;
    Error.captureStackTrace(this, this.constructor);
  }
}

export class ValidationError extends AppError {
  constructor(message: string) {
    super(message, 400, 'VALIDATION_ERROR');
  }
}

export class AuthenticationError extends AppError {
  constructor(message: string = 'Authentication failed') {
    super(message, 401, 'AUTHENTICATION_ERROR');
  }
}

export class AuthorizationError extends AppError {
  constructor(message: string = 'Insufficient permissions') {
    super(message, 403, 'AUTHORIZATION_ERROR');
  }
}

export class NotFoundError extends AppError {
  constructor(message: string = 'Resource not found') {
    super(message, 404, 'NOT_FOUND_ERROR');
  }
}

// Main error handling middleware
export const errorHandler = (
  err: CustomError,
  req: Request,
  res: Response,
  next: NextFunction
) => {
  let error = { ...err };
  error.message = err.message;

  // Set default status code
  if (!error.statusCode) {
    error.statusCode = 500;
  }

  // Log the error
  logger.error('Error occurred:', {
    message: error.message,
    stack: error.stack,
    url: req.originalUrl,
    method: req.method,
    ip: req.ip,
    statusCode: error.statusCode,
  });

  // Send error response
  if (process.env.NODE_ENV === 'development') {
    res.status(error.statusCode).json({
      status: 'error',
      error: error,
      message: error.message,
      stack: error.stack,
      timestamp: new Date().toISOString(),
    });
  } else {
    // Production error response (sanitized)
    if (error.isOperational) {
      res.status(error.statusCode).json({
        status: 'error',
        message: error.message,
        code: error.code,
        timestamp: new Date().toISOString(),
      });
    } else {
      res.status(500).json({
        status: 'error',
        message: 'Something went wrong. Please try again later.',
        code: 'INTERNAL_SERVER_ERROR',
        timestamp: new Date().toISOString(),
      });
    }
  }
};

// Async error wrapper
export const catchAsync = (fn: Function) => {
  return (req: Request, res: Response, next: NextFunction) => {
    fn(req, res, next).catch(next);
  };
}; 