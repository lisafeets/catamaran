import { CallType, SmsType, AlertType, AlertSeverity } from '@prisma/client';
import { db } from '@/database/prisma';
import { logger } from '@/utils/logger';
import { hashPhoneNumber, encryptJSON } from '@/utils/encryption';
import { notificationService } from './notificationService';

interface CallLogInput {
  phoneNumber: string;
  contactName?: string;
  duration: number;
  callType: CallType;
  timestamp: Date;
  isKnownContact: boolean;
}

interface SmsLogInput {
  phoneNumber: string;
  contactName?: string;
  messageCount: number;
  smsType: SmsType;
  timestamp: Date;
  isKnownContact: boolean;
}

interface ActivitySummary {
  date: string;
  totalCalls: number;
  totalSms: number;
  unknownCalls: number;
  unknownSms: number;
  suspiciousActivity: number;
  avgCallDuration: number;
}

interface RiskAnalysis {
  riskScore: number;
  riskFactors: string[];
  alerts: AlertType[];
}

/**
 * Process and store call logs from mobile app
 */
export const processCallLogs = async (
  userId: string, 
  callLogs: CallLogInput[]
): Promise<void> => {
  try {
    // Process each call log
    const processedLogs = callLogs.map(log => {
      const phoneNumberHash = hashPhoneNumber(log.phoneNumber);
      const riskAnalysis = analyzeCallRisk(log);
      
      return {
        userId,
        phoneNumberHash,
        contactName: log.contactName ? encryptJSON(log.contactName) : null,
        duration: log.duration,
        callType: log.callType,
        timestamp: log.timestamp,
        isKnownContact: log.isKnownContact,
        suspiciousPatterns: riskAnalysis.riskFactors.length > 0 
          ? encryptJSON(riskAnalysis.riskFactors) 
          : null,
        riskScore: riskAnalysis.riskScore,
        processedAt: new Date(),
        analysisVersion: '1.0'
      };
    });

    // Bulk insert call logs
    await db.callLog.createMany({
      data: processedLogs,
      skipDuplicates: true
    });

    // Analyze for suspicious patterns
    await analyzeRecentActivity(userId, 'calls');

    logger.info('Call logs processed', { 
      userId, 
      count: callLogs.length 
    });

  } catch (error) {
    logger.error('Failed to process call logs', { userId, error });
    throw error;
  }
};

/**
 * Process and store SMS logs from mobile app
 */
export const processSmsLogs = async (
  userId: string, 
  smsLogs: SmsLogInput[]
): Promise<void> => {
  try {
    // Process each SMS log
    const processedLogs = smsLogs.map(log => {
      const phoneNumberHash = hashPhoneNumber(log.phoneNumber);
      const riskAnalysis = analyzeSmsRisk(log);
      
      return {
        userId,
        phoneNumberHash,
        contactName: log.contactName ? encryptJSON(log.contactName) : null,
        messageCount: log.messageCount,
        smsType: log.smsType,
        timestamp: log.timestamp,
        isKnownContact: log.isKnownContact,
        frequencyPattern: riskAnalysis.riskFactors.length > 0 
          ? encryptJSON(riskAnalysis.riskFactors) 
          : null,
        riskScore: riskAnalysis.riskScore,
        processedAt: new Date(),
        analysisVersion: '1.0'
      };
    });

    // Bulk insert SMS logs
    await db.smsLog.createMany({
      data: processedLogs,
      skipDuplicates: true
    });

    // Analyze for suspicious patterns
    await analyzeRecentActivity(userId, 'sms');

    logger.info('SMS logs processed', { 
      userId, 
      count: smsLogs.length 
    });

  } catch (error) {
    logger.error('Failed to process SMS logs', { userId, error });
    throw error;
  }
};

/**
 * Get activity summary for a senior user
 */
export const getActivitySummary = async (
  userId: string,
  startDate: Date,
  endDate: Date
): Promise<ActivitySummary[]> => {
  try {
    // Get call logs in date range
    const callLogs = await db.callLog.findMany({
      where: {
        userId,
        timestamp: {
          gte: startDate,
          lte: endDate
        }
      }
    });

    // Get SMS logs in date range
    const smsLogs = await db.smsLog.findMany({
      where: {
        userId,
        timestamp: {
          gte: startDate,
          lte: endDate
        }
      }
    });

    // Group by date and calculate summary
    const summaryMap = new Map<string, ActivitySummary>();

    // Process call logs
    callLogs.forEach(log => {
      const date = log.timestamp.toISOString().split('T')[0];
      const summary = summaryMap.get(date) || {
        date,
        totalCalls: 0,
        totalSms: 0,
        unknownCalls: 0,
        unknownSms: 0,
        suspiciousActivity: 0,
        avgCallDuration: 0
      };

      summary.totalCalls++;
      if (!log.isKnownContact) summary.unknownCalls++;
      if (log.riskScore && log.riskScore > 0.5) summary.suspiciousActivity++;
      
      summaryMap.set(date, summary);
    });

    // Process SMS logs
    smsLogs.forEach(log => {
      const date = log.timestamp.toISOString().split('T')[0];
      const summary = summaryMap.get(date) || {
        date,
        totalCalls: 0,
        totalSms: 0,
        unknownCalls: 0,
        unknownSms: 0,
        suspiciousActivity: 0,
        avgCallDuration: 0
      };

      summary.totalSms += log.messageCount;
      if (!log.isKnownContact) summary.unknownSms += log.messageCount;
      if (log.riskScore && log.riskScore > 0.5) summary.suspiciousActivity++;
      
      summaryMap.set(date, summary);
    });

    // Calculate average call duration
    summaryMap.forEach((summary, date) => {
      const dayCalls = callLogs.filter(log => 
        log.timestamp.toISOString().split('T')[0] === date
      );
      
      if (dayCalls.length > 0) {
        summary.avgCallDuration = dayCalls.reduce((sum, log) => sum + log.duration, 0) / dayCalls.length;
      }
    });

    return Array.from(summaryMap.values()).sort((a, b) => a.date.localeCompare(b.date));

  } catch (error) {
    logger.error('Failed to get activity summary', { userId, error });
    throw error;
  }
};

/**
 * Analyze individual call for risk factors
 */
const analyzeCallRisk = (call: CallLogInput): RiskAnalysis => {
  const riskFactors: string[] = [];
  let riskScore = 0;

  // Unknown number risk
  if (!call.isKnownContact) {
    riskFactors.push('Unknown caller');
    riskScore += 0.3;
  }

  // Very short calls (potential robocalls)
  if (call.duration < 10 && call.callType === CallType.INCOMING) {
    riskFactors.push('Very short incoming call');
    riskScore += 0.4;
  }

  // Very long calls from unknown numbers
  if (call.duration > 1800 && !call.isKnownContact) { // 30+ minutes
    riskFactors.push('Long call from unknown number');
    riskScore += 0.5;
  }

  // Late night or early morning calls
  const hour = call.timestamp.getHours();
  if ((hour < 7 || hour > 21) && !call.isKnownContact) {
    riskFactors.push('Off-hours call from unknown number');
    riskScore += 0.3;
  }

  const alerts: AlertType[] = [];
  if (riskScore > 0.7) {
    alerts.push(AlertType.SCAM_DETECTION);
  } else if (riskScore > 0.5) {
    alerts.push(AlertType.SUSPICIOUS_SMS_PATTERN);
  }

  return {
    riskScore: Math.min(riskScore, 1.0),
    riskFactors,
    alerts
  };
};

/**
 * Analyze individual SMS for risk factors
 */
const analyzeSmsRisk = (sms: SmsLogInput): RiskAnalysis => {
  const riskFactors: string[] = [];
  let riskScore = 0;

  // Unknown number risk
  if (!sms.isKnownContact) {
    riskFactors.push('Unknown sender');
    riskScore += 0.3;
  }

  // Multiple messages in short time (potential spam)
  if (sms.messageCount > 3) {
    riskFactors.push('Multiple messages from same number');
    riskScore += 0.4;
  }

  // Late night or early morning messages
  const hour = sms.timestamp.getHours();
  if ((hour < 7 || hour > 21) && !sms.isKnownContact) {
    riskFactors.push('Off-hours message from unknown number');
    riskScore += 0.3;
  }

  const alerts: AlertType[] = [];
  if (riskScore > 0.7) {
    alerts.push(AlertType.SUSPICIOUS_SMS_PATTERN);
  } else if (riskScore > 0.5) {
    alerts.push(AlertType.UNUSUAL_ACTIVITY);
  }

  return {
    riskScore: Math.min(riskScore, 1.0),
    riskFactors,
    alerts
  };
};

/**
 * Analyze recent activity for patterns that might need family alerts
 */
const analyzeRecentActivity = async (userId: string, type: 'calls' | 'sms'): Promise<void> => {
  try {
    const last24Hours = new Date(Date.now() - 24 * 60 * 60 * 1000);
    
    if (type === 'calls') {
      // Check for frequent unknown calls
      const unknownCalls = await db.callLog.count({
        where: {
          userId,
          timestamp: { gte: last24Hours },
          isKnownContact: false
        }
      });

      if (unknownCalls >= 10) {
        await notificationService.createAlert({
          senderId: userId,
          type: AlertType.FREQUENT_UNKNOWN_CALLS,
          severity: AlertSeverity.HIGH,
          title: 'Frequent Unknown Calls Detected',
          message: `${unknownCalls} calls from unknown numbers in the last 24 hours`,
          metadata: encryptJSON({ count: unknownCalls, period: '24h' })
        });
      }
    } else {
      // Check for frequent unknown SMS
      const unknownSms = await db.smsLog.aggregate({
        where: {
          userId,
          timestamp: { gte: last24Hours },
          isKnownContact: false
        },
        _sum: {
          messageCount: true
        }
      });

      const count = unknownSms._sum.messageCount || 0;
      if (count >= 20) {
        await notificationService.createAlert({
          senderId: userId,
          type: AlertType.SUSPICIOUS_SMS_PATTERN,
          severity: AlertSeverity.HIGH,
          title: 'Unusual SMS Activity Detected',
          message: `${count} messages from unknown numbers in the last 24 hours`,
          metadata: encryptJSON({ count, period: '24h' })
        });
      }
    }

  } catch (error) {
    logger.error('Failed to analyze recent activity', { userId, type, error });
  }
};

/**
 * Clean up old activity logs based on retention policy
 */
export const cleanupOldLogs = async (): Promise<void> => {
  try {
    const retentionDate = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000); // 30 days

    // Delete old call logs
    const deletedCalls = await db.callLog.deleteMany({
      where: {
        timestamp: { lt: retentionDate }
      }
    });

    // Delete old SMS logs
    const deletedSms = await db.smsLog.deleteMany({
      where: {
        timestamp: { lt: retentionDate }
      }
    });

    logger.info('Old logs cleaned up', {
      deletedCalls: deletedCalls.count,
      deletedSms: deletedSms.count,
      retentionDate
    });

  } catch (error) {
    logger.error('Failed to cleanup old logs', { error });
    throw error;
  }
}; 