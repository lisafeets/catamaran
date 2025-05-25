import { Request, Response, NextFunction } from 'express';
import { logger } from '@/utils/logger';

export const auditMiddleware = (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  // Log the incoming request
  logger.info('API Request', {
    method: req.method,
    url: req.originalUrl,
    ip: req.ip,
    userAgent: req.get('User-Agent'),
    userId: req.user?.id || 'anonymous',
    timestamp: new Date().toISOString(),
  });

  // Continue to next middleware
  next();
}; 