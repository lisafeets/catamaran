import express from 'express';
import { body, validationResult, param } from 'express-validator';
import { prisma } from '../server';
import { ApiError, asyncHandler } from '../middleware/errorHandler';
import { authenticateToken, requireRole } from '../middleware/auth';
import logger from '../utils/logger';

const router = express.Router();

// Validation for activity sync
const activitySyncValidation = [
  body('callLogs')
    .optional()
    .isArray()
    .withMessage('callLogs must be an array'),
  body('callLogs.*.phoneNumber')
    .isString()
    .isLength({ min: 1 })
    .withMessage('Phone number is required'),
  body('callLogs.*.duration')
    .isInt({ min: 0 })
    .withMessage('Duration must be a positive integer'),
  body('callLogs.*.timestamp')
    .isISO8601()
    .toDate()
    .withMessage('Valid timestamp is required'),
  body('callLogs.*.callType')
    .isIn(['incoming', 'outgoing', 'missed'])
    .withMessage('Call type must be incoming, outgoing, or missed'),
  body('smsLogs')
    .optional()
    .isArray()
    .withMessage('smsLogs must be an array'),
  body('smsLogs.*.senderNumber')
    .isString()
    .isLength({ min: 1 })
    .withMessage('Sender number is required'),
  body('smsLogs.*.timestamp')
    .isISO8601()
    .toDate()
    .withMessage('Valid timestamp is required'),
  body('smsLogs.*.messageType')
    .isIn(['received', 'sent'])
    .withMessage('Message type must be received or sent')
];

// Helper function to calculate risk score
const calculateRiskScore = (activity: any, type: 'call' | 'sms'): number => {
  let score = 0;

  if (type === 'call') {
    // Unknown number increases risk
    if (!activity.isKnownContact) score += 0.3;
    
    // Very short calls are suspicious
    if (activity.duration < 10) score += 0.4;
    
    // Very long calls from unknown numbers
    if (activity.duration > 1800 && !activity.isKnownContact) score += 0.5;
    
    // Off-hours calls (before 7 AM or after 9 PM)
    const hour = new Date(activity.timestamp).getHours();
    if (hour < 7 || hour > 21) score += 0.3;
    
    // Missed calls from unknown numbers
    if (activity.callType === 'missed' && !activity.isKnownContact) score += 0.2;
  }

  if (type === 'sms') {
    // Unknown sender increases risk
    if (!activity.isKnownContact) score += 0.4;
    
    // Messages with links are more risky
    if (activity.hasLink) score += 0.5;
    
    // Off-hours messages
    const hour = new Date(activity.timestamp).getHours();
    if (hour < 7 || hour > 21) score += 0.2;
    
    // High message count might indicate spam
    if (activity.messageCount > 5) score += 0.3;
  }

  return Math.min(score, 1.0); // Cap at 1.0
};

// POST /api/activity/sync - Android app uploads activity data
router.post('/sync', authenticateToken, requireRole(['SENIOR']), activitySyncValidation, asyncHandler(async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    throw new ApiError('Validation failed', 400);
  }

  const { callLogs = [], smsLogs = [] } = req.body;
  const userId = req.user!.id;

  let processedCalls = 0;
  let processedSms = 0;

  // Process call logs
  if (callLogs.length > 0) {
    const callData = callLogs.map((log: any) => ({
      seniorId: userId,
      phoneNumber: log.phoneNumber, // Should be hashed by Android app
      contactName: log.contactName || null, // Should be encrypted by Android app
      duration: log.duration,
      timestamp: new Date(log.timestamp),
      callType: log.callType,
      isKnownContact: log.isKnownContact || false,
      riskScore: calculateRiskScore(log, 'call')
    }));

    // Batch insert call logs
    await prisma.callLog.createMany({
      data: callData,
      skipDuplicates: true
    });

    processedCalls = callData.length;
  }

  // Process SMS logs
  if (smsLogs.length > 0) {
    const smsData = smsLogs.map((log: any) => ({
      seniorId: userId,
      senderNumber: log.senderNumber, // Should be hashed by Android app
      contactName: log.contactName || null, // Should be encrypted by Android app
      messageCount: log.messageCount || 1,
      timestamp: new Date(log.timestamp),
      messageType: log.messageType,
      isKnownContact: log.isKnownContact || false,
      hasLink: log.hasLink || false,
      riskScore: calculateRiskScore(log, 'sms')
    }));

    // Batch insert SMS logs
    await prisma.smsLog.createMany({
      data: smsData,
      skipDuplicates: true
    });

    processedSms = smsData.length;
  }

  logger.info('Activity sync completed', {
    userId,
    processedCalls,
    processedSms,
    timestamp: new Date().toISOString()
  });

  res.json({
    success: true,
    message: 'Activity synced successfully',
    processed: {
      calls: processedCalls,
      sms: processedSms
    }
  });
}));

// GET /api/activity/:familyId - Get dashboard data for family member
router.get('/:familyId', authenticateToken, param('familyId').isString(), asyncHandler(async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    throw new ApiError('Invalid family ID', 400);
  }

  const { familyId } = req.params;
  const requestingUserId = req.user!.id;

  // Check if requesting user has access to this family member's data
  const familyConnection = await prisma.familyConnection.findFirst({
    where: {
      seniorId: familyId,
      familyId: requestingUserId,
      isActive: true
    },
    include: {
      senior: {
        select: {
          id: true,
          firstName: true,
          lastName: true,
          email: true,
          phone: true
        }
      }
    }
  });

  // Also allow seniors to access their own data
  const isOwnData = familyId === requestingUserId;

  if (!familyConnection && !isOwnData) {
    throw new ApiError('Access denied - not authorized to view this family member\'s activity', 403);
  }

  // Get recent activity (last 30 days)
  const thirtyDaysAgo = new Date();
  thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

  // Get call logs with risk analysis
  const callLogs = await prisma.callLog.findMany({
    where: {
      seniorId: familyId,
      timestamp: {
        gte: thirtyDaysAgo
      }
    },
    select: {
      id: true,
      phoneNumber: true, // This is hashed
      contactName: true, // This is encrypted
      duration: true,
      timestamp: true,
      callType: true,
      riskScore: true,
      isKnownContact: true
    },
    orderBy: {
      timestamp: 'desc'
    },
    take: 100
  });

  // Get SMS logs with risk analysis
  const smsLogs = await prisma.smsLog.findMany({
    where: {
      seniorId: familyId,
      timestamp: {
        gte: thirtyDaysAgo
      }
    },
    select: {
      id: true,
      senderNumber: true, // This is hashed
      contactName: true, // This is encrypted
      messageCount: true,
      timestamp: true,
      messageType: true,
      riskScore: true,
      isKnownContact: true,
      hasLink: true
    },
    orderBy: {
      timestamp: 'desc'
    },
    take: 100
  });

  // Calculate statistics
  const stats = {
    totalCalls: callLogs.length,
    totalSms: smsLogs.length,
    highRiskCalls: callLogs.filter(call => call.riskScore > 0.7).length,
    highRiskSms: smsLogs.filter(sms => sms.riskScore > 0.7).length,
    unknownContacts: {
      calls: callLogs.filter(call => !call.isKnownContact).length,
      sms: smsLogs.filter(sms => sms.isKnownContact).length
    },
    recentAlerts: [...callLogs, ...smsLogs]
      .filter(activity => activity.riskScore > 0.5)
      .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
      .slice(0, 10)
  };

  // Get senior info
  const seniorInfo = familyConnection?.senior || await prisma.user.findUnique({
    where: { id: familyId },
    select: {
      id: true,
      firstName: true,
      lastName: true,
      email: true,
      phone: true
    }
  });

  logger.info('Activity data requested', {
    requestingUserId,
    familyId,
    callCount: callLogs.length,
    smsCount: smsLogs.length
  });

  res.json({
    success: true,
    senior: seniorInfo,
    activity: {
      calls: callLogs,
      sms: smsLogs
    },
    stats,
    lastUpdated: new Date().toISOString()
  });
}));

// GET /api/activity/alerts/:familyId - Get high-risk alerts for family member
router.get('/alerts/:familyId', authenticateToken, param('familyId').isString(), asyncHandler(async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    throw new ApiError('Invalid family ID', 400);
  }

  const { familyId } = req.params;
  const requestingUserId = req.user!.id;

  // Check authorization (same as above)
  const familyConnection = await prisma.familyConnection.findFirst({
    where: {
      seniorId: familyId,
      familyId: requestingUserId,
      isActive: true
    }
  });

  const isOwnData = familyId === requestingUserId;

  if (!familyConnection && !isOwnData) {
    throw new ApiError('Access denied', 403);
  }

  // Get high-risk activity from last 7 days
  const sevenDaysAgo = new Date();
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);

  const highRiskCalls = await prisma.callLog.findMany({
    where: {
      seniorId: familyId,
      riskScore: {
        gte: 0.5
      },
      timestamp: {
        gte: sevenDaysAgo
      }
    },
    orderBy: {
      riskScore: 'desc'
    },
    take: 20
  });

  const highRiskSms = await prisma.smsLog.findMany({
    where: {
      seniorId: familyId,
      riskScore: {
        gte: 0.5
      },
      timestamp: {
        gte: sevenDaysAgo
      }
    },
    orderBy: {
      riskScore: 'desc'
    },
    take: 20
  });

  res.json({
    success: true,
    alerts: {
      calls: highRiskCalls,
      sms: highRiskSms
    },
    summary: {
      totalAlerts: highRiskCalls.length + highRiskSms.length,
      highestRiskScore: Math.max(
        ...highRiskCalls.map(c => c.riskScore),
        ...highRiskSms.map(s => s.riskScore),
        0
      )
    }
  });
}));

export default router; 