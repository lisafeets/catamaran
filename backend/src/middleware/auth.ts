import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { prisma } from '../server';
import { ApiError } from './errorHandler';
import logger from '../utils/logger';

interface JwtPayload {
  id: string;
  email: string;
  role: string;
  iat?: number;
  exp?: number;
}

// Extend Request interface to include user
declare global {
  namespace Express {
    interface Request {
      user?: {
        id: string;
        email: string;
        role: string;
      };
    }
  }
}

export const authenticateToken = async (
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> => {
  try {
    const authHeader = req.headers.authorization;
    const token = authHeader && authHeader.split(' ')[1]; // Bearer TOKEN

    if (!token) {
      throw new ApiError('Access token required', 401);
    }

    // Verify token
    const jwtSecret = process.env.JWT_SECRET || 'catamaran-default-jwt-secret-for-testing-only-change-in-production';
    
    const decoded = jwt.verify(token, jwtSecret) as JwtPayload;

    // TEMPORARILY DISABLED FOR TESTING - Skip database user lookup
    /*
    // Check if user still exists and is active
    const user = await prisma.user.findUnique({
      where: { id: decoded.id },
      select: {
        id: true,
        email: true,
        role: true,
        isActive: true
      }
    });

    if (!user || !user.isActive) {
      throw new ApiError('User not found or inactive', 401);
    }
    */

    // Add user to request object (using decoded token data for testing)
    req.user = {
      id: decoded.id,
      email: decoded.email,
      role: decoded.role
    };

    next();
  } catch (error) {
    if (error instanceof jwt.JsonWebTokenError) {
      logger.warn('Invalid JWT token', { token: req.headers.authorization });
      next(new ApiError('Invalid token', 401));
    } else if (error instanceof jwt.TokenExpiredError) {
      logger.warn('Expired JWT token', { token: req.headers.authorization });
      next(new ApiError('Token expired', 401));
    } else {
      next(error);
    }
  }
};

// Middleware to check if user has required role
export const requireRole = (roles: string[]) => {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!req.user) {
      throw new ApiError('Authentication required', 401);
    }

    if (!roles.includes(req.user.role)) {
      throw new ApiError('Insufficient permissions', 403);
    }

    next();
  };
};

// Middleware to ensure user is accessing their own data or is authorized
export const requireOwnershipOrRole = (roles: string[] = ['ADMIN']) => {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!req.user) {
      throw new ApiError('Authentication required', 401);
    }

    const userId = req.params.userId || req.body.userId;
    
    // Allow if user is accessing their own data
    if (req.user.id === userId) {
      return next();
    }

    // Allow if user has required role
    if (roles.includes(req.user.role)) {
      return next();
    }

    throw new ApiError('Access denied', 403);
  };
}; 