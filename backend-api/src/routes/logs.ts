import { Router, Request, Response } from 'express';
import { body, query, validationResult } from 'express-validator';
import { CallType, SmsType } from '@prisma/client';
import * as activityService from '@/services/activityService';
import { logger } from '@/utils/logger';
import { authMiddleware } from '@/middleware/auth';

const router = Router();

// All routes require authentication
router.use(authMiddleware);

/**
 * Upload call logs from mobile device
 * POST /api/logs/calls
 */
router.post('/calls', [
  body('logs')
    .isArray({ min: 1 })
    .withMessage('Logs array is required with at least one entry'),
  body('logs.*.phoneNumber')
    .isString()
    .isLength({ min: 5, max: 20 })
    .withMessage('Valid phone number is required'),
  body('logs.*.duration')
    .isInt({ min: 0 })
    .withMessage('Duration must be a non-negative integer'),
  body('logs.*.callType')
    .isIn([CallType.INCOMING, CallType.OUTGOING, CallType.MISSED])
    .withMessage('Call type must be INCOMING, OUTGOING, or MISSED'),
  body('logs.*.timestamp')
    .isISO8601()
    .withMessage('Valid ISO 8601 timestamp is required'),
  body('logs.*.isKnownContact')
    .isBoolean()
    .withMessage('isKnownContact must be a boolean'),
  body('logs.*.contactName')
    .optional()
    .isString()
    .isLength({ max: 100 })
    .withMessage('Contact name must be a string with max 100 characters')
], async (req: Request, res: Response) => {
  try {
    // Check validation errors
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        error: 'Validation failed',
        details: errors.array()
      });
    }

    if (!req.user?.id) {
      return res.status(401).json({
        error: 'Authentication required'
      });
    }

    const { logs } = req.body;

    // Convert timestamps to Date objects
    const processedLogs = logs.map((log: any) => ({
      ...log,
      timestamp: new Date(log.timestamp)
    }));

    // Process call logs
    await activityService.processCallLogs(req.user.id, processedLogs);

    logger.info('Call logs uploaded successfully', {
      userId: req.user.id,
      count: logs.length
    });

    res.json({
      success: true,
      message: `${logs.length} call logs processed successfully`,
      processed: logs.length
    });

  } catch (error) {
    logger.error('Call logs upload failed', {
      userId: req.user?.id,
      error: (error as Error).message
    });

    res.status(500).json({
      error: 'Failed to process call logs',
      message: 'An internal error occurred while processing call logs'
    });
  }
});

/**
 * Upload SMS logs from mobile device
 * POST /api/logs/sms
 */
router.post('/sms', [
  body('logs')
    .isArray({ min: 1 })
    .withMessage('Logs array is required with at least one entry'),
  body('logs.*.phoneNumber')
    .isString()
    .isLength({ min: 5, max: 20 })
    .withMessage('Valid phone number is required'),
  body('logs.*.messageCount')
    .isInt({ min: 1 })
    .withMessage('Message count must be a positive integer'),
  body('logs.*.smsType')
    .isIn([SmsType.INCOMING, SmsType.OUTGOING])
    .withMessage('SMS type must be INCOMING or OUTGOING'),
  body('logs.*.timestamp')
    .isISO8601()
    .withMessage('Valid ISO 8601 timestamp is required'),
  body('logs.*.isKnownContact')
    .isBoolean()
    .withMessage('isKnownContact must be a boolean'),
  body('logs.*.contactName')
    .optional()
    .isString()
    .isLength({ max: 100 })
    .withMessage('Contact name must be a string with max 100 characters')
], async (req: Request, res: Response) => {
  try {
    // Check validation errors
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        error: 'Validation failed',
        details: errors.array()
      });
    }

    if (!req.user?.id) {
      return res.status(401).json({
        error: 'Authentication required'
      });
    }

    const { logs } = req.body;

    // Convert timestamps to Date objects
    const processedLogs = logs.map((log: any) => ({
      ...log,
      timestamp: new Date(log.timestamp)
    }));

    // Process SMS logs
    await activityService.processSmsLogs(req.user.id, processedLogs);

    logger.info('SMS logs uploaded successfully', {
      userId: req.user.id,
      count: logs.length
    });

    res.json({
      success: true,
      message: `${logs.length} SMS logs processed successfully`,
      processed: logs.length
    });

  } catch (error) {
    logger.error('SMS logs upload failed', {
      userId: req.user?.id,
      error: (error as Error).message
    });

    res.status(500).json({
      error: 'Failed to process SMS logs',
      message: 'An internal error occurred while processing SMS logs'
    });
  }
});

/**
 * Get activity summary for date range
 * GET /api/logs/summary
 */
router.get('/summary', [
  query('startDate')
    .isISO8601()
    .withMessage('Valid start date (ISO 8601) is required'),
  query('endDate')
    .isISO8601()
    .withMessage('Valid end date (ISO 8601) is required'),
  query('seniorId')
    .optional()
    .isString()
    .withMessage('Senior ID must be a string')
], async (req: Request, res: Response) => {
  try {
    // Check validation errors
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        error: 'Validation failed',
        details: errors.array()
      });
    }

    if (!req.user?.id) {
      return res.status(401).json({
        error: 'Authentication required'
      });
    }

    const { startDate, endDate, seniorId } = req.query;

    // Determine user ID to query
    let targetUserId = req.user.id;
    
    // If family member requesting another user's data, verify permissions
    if (seniorId && seniorId !== req.user.id) {
      if (req.user.role !== 'FAMILY_MEMBER') {
        return res.status(403).json({
          error: 'Access denied',
          message: 'Only family members can view other users\' activity'
        });
      }
      
      // In production, verify family connection
      targetUserId = seniorId as string;
    }

    // Get activity summary
    const summary = await activityService.getActivitySummary(
      targetUserId,
      new Date(startDate as string),
      new Date(endDate as string)
    );

    logger.info('Activity summary retrieved', {
      userId: req.user.id,
      targetUserId,
      dateRange: { startDate, endDate },
      resultCount: summary.length
    });

    res.json({
      success: true,
      summary,
      dateRange: {
        startDate: startDate as string,
        endDate: endDate as string
      }
    });

  } catch (error) {
    logger.error('Activity summary retrieval failed', {
      userId: req.user?.id,
      error: (error as Error).message
    });

    res.status(500).json({
      error: 'Failed to retrieve activity summary',
      message: 'An internal error occurred while retrieving activity summary'
    });
  }
});

/**
 * Get today's activity for quick view
 * GET /api/logs/today
 */
router.get('/today', async (req: Request, res: Response) => {
  try {
    if (!req.user?.id) {
      return res.status(401).json({
        error: 'Authentication required'
      });
    }

    const today = new Date();
    const startOfDay = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    const endOfDay = new Date(startOfDay);
    endOfDay.setDate(endOfDay.getDate() + 1);

    // Get today's activity summary
    const summary = await activityService.getActivitySummary(
      req.user.id,
      startOfDay,
      endOfDay
    );

    const todaySummary = summary.length > 0 ? summary[0] : {
      date: today.toISOString().split('T')[0],
      totalCalls: 0,
      totalSms: 0,
      unknownCalls: 0,
      unknownSms: 0,
      suspiciousActivity: 0,
      avgCallDuration: 0
    };

    logger.info('Today\'s activity retrieved', {
      userId: req.user.id,
      summary: todaySummary
    });

    res.json({
      success: true,
      today: todaySummary
    });

  } catch (error) {
    logger.error('Today\'s activity retrieval failed', {
      userId: req.user?.id,
      error: (error as Error).message
    });

    res.status(500).json({
      error: 'Failed to retrieve today\'s activity',
      message: 'An internal error occurred while retrieving today\'s activity'
    });
  }
});

/**
 * Health check endpoint for mobile app
 * GET /api/logs/health
 */
router.get('/health', async (req: Request, res: Response) => {
  try {
    if (!req.user?.id) {
      return res.status(401).json({
        error: 'Authentication required'
      });
    }

    // Simple health check - verify user exists and is active
    res.json({
      success: true,
      status: 'healthy',
      userId: req.user.id,
      timestamp: new Date().toISOString()
    });

  } catch (error) {
    logger.error('Health check failed', {
      userId: req.user?.id,
      error: (error as Error).message
    });

    res.status(500).json({
      error: 'Health check failed'
    });
  }
});

export default router; 