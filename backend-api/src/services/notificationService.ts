import { AlertType, AlertSeverity, AlertStatus, ConnectionStatus } from '@prisma/client';
import { db } from '@/database/prisma';
import { logger } from '@/utils/logger';
import { encryptJSON, decryptJSON } from '@/utils/encryption';
import { websocketService } from './websocketService';

interface CreateAlertInput {
  senderId: string;
  type: AlertType;
  severity: AlertSeverity;
  title: string;
  message: string;
  metadata?: string;
}

interface NotificationPreferences {
  emailNotifications: boolean;
  smsNotifications: boolean;
  pushNotifications: boolean;
}

/**
 * Create and distribute an alert to family members
 */
export const createAlert = async (input: CreateAlertInput): Promise<void> => {
  try {
    const { senderId, type, severity, title, message, metadata } = input;

    // Get active family connections for the senior
    const connections = await db.familyConnection.findMany({
      where: {
        seniorId: senderId,
        status: ConnectionStatus.ACTIVE
      },
      include: {
        guardian: {
          include: {
            familyProfile: true
          }
        }
      }
    });

    if (connections.length === 0) {
      logger.info('No active family connections found for alert', { senderId, type });
      return;
    }

    // Create alerts for each family member
    const alertPromises = connections.map(async (connection) => {
      const familyMember = connection.guardian;
      
      // Create alert record
      const alert = await db.alert.create({
        data: {
          senderId,
          receiverId: familyMember.id,
          type,
          severity,
          title,
          message,
          metadata,
          status: AlertStatus.PENDING
        }
      });

      // Send notifications based on preferences
      await sendNotifications(alert.id, familyMember, { title, message, type, severity });

      logger.info('Alert created and sent', {
        alertId: alert.id,
        senderId,
        receiverId: familyMember.id,
        type,
        severity
      });

      return alert;
    });

    await Promise.all(alertPromises);

  } catch (error) {
    logger.error('Failed to create alert', { input, error });
    throw error;
  }
};

/**
 * Send notifications through various channels
 */
const sendNotifications = async (
  alertId: string,
  recipient: any,
  alertData: {
    title: string;
    message: string;
    type: AlertType;
    severity: AlertSeverity;
  }
): Promise<void> => {
  const preferences = recipient.familyProfile as NotificationPreferences;

  try {
    // Always send WebSocket notification for real-time updates
    await websocketService.sendToUser(recipient.id, {
      type: 'alert',
      data: {
        id: alertId,
        title: alertData.title,
        message: alertData.message,
        alertType: alertData.type,
        severity: alertData.severity,
        timestamp: new Date().toISOString()
      }
    });

    // Send email notification if enabled
    if (preferences?.emailNotifications) {
      await sendEmailNotification(recipient.email, alertData);
    }

    // Send SMS notification if enabled and phone number available
    if (preferences?.smsNotifications && recipient.phone) {
      await sendSmsNotification(recipient.phone, alertData);
    }

    // Send push notification if enabled
    if (preferences?.pushNotifications) {
      await sendPushNotification(recipient.id, alertData);
    }

    // Update alert status to sent
    await db.alert.update({
      where: { id: alertId },
      data: { 
        status: AlertStatus.SENT,
        sentAt: new Date()
      }
    });

  } catch (error) {
    logger.error('Failed to send notifications', { alertId, recipientId: recipient.id, error });
    
    // Update alert status to indicate failure
    await db.alert.update({
      where: { id: alertId },
      data: { status: AlertStatus.PENDING }
    });
  }
};

/**
 * Send email notification
 */
const sendEmailNotification = async (
  email: string,
  alertData: {
    title: string;
    message: string;
    type: AlertType;
    severity: AlertSeverity;
  }
): Promise<void> => {
  try {
    // In production, integrate with email service (SendGrid, AWS SES, etc.)
    logger.info('Email notification sent', { email, alertType: alertData.type });
    
    // Placeholder for actual email sending
    // await emailService.send({
    //   to: email,
    //   subject: `Catamaran Alert: ${alertData.title}`,
    //   body: alertData.message,
    //   priority: alertData.severity === AlertSeverity.CRITICAL ? 'high' : 'normal'
    // });

  } catch (error) {
    logger.error('Failed to send email notification', { email, error });
    throw error;
  }
};

/**
 * Send SMS notification
 */
const sendSmsNotification = async (
  phone: string,
  alertData: {
    title: string;
    message: string;
    type: AlertType;
    severity: AlertSeverity;
  }
): Promise<void> => {
  try {
    // In production, integrate with SMS service (Twilio, AWS SNS, etc.)
    logger.info('SMS notification sent', { phone, alertType: alertData.type });
    
    // Placeholder for actual SMS sending
    // await smsService.send({
    //   to: phone,
    //   message: `Catamaran Alert: ${alertData.title}\n${alertData.message}`,
    //   priority: alertData.severity === AlertSeverity.CRITICAL
    // });

  } catch (error) {
    logger.error('Failed to send SMS notification', { phone, error });
    throw error;
  }
};

/**
 * Send push notification
 */
const sendPushNotification = async (
  userId: string,
  alertData: {
    title: string;
    message: string;
    type: AlertType;
    severity: AlertSeverity;
  }
): Promise<void> => {
  try {
    // In production, integrate with push notification service (Firebase, OneSignal, etc.)
    logger.info('Push notification sent', { userId, alertType: alertData.type });
    
    // Placeholder for actual push notification
    // await pushService.send({
    //   userId,
    //   title: alertData.title,
    //   body: alertData.message,
    //   priority: alertData.severity === AlertSeverity.CRITICAL ? 'high' : 'normal',
    //   data: { alertType: alertData.type }
    // });

  } catch (error) {
    logger.error('Failed to send push notification', { userId, error });
    throw error;
  }
};

/**
 * Get alerts for a family member
 */
export const getAlertsForUser = async (
  userId: string,
  limit: number = 50,
  offset: number = 0
): Promise<any[]> => {
  try {
    const alerts = await db.alert.findMany({
      where: { receiverId: userId },
      orderBy: { sentAt: 'desc' },
      take: limit,
      skip: offset,
      include: {
        sender: {
          select: {
            id: true,
            firstName: true,
            lastName: true,
            email: true
          }
        }
      }
    });

    return alerts.map(alert => ({
      id: alert.id,
      type: alert.type,
      severity: alert.severity,
      title: alert.title,
      message: alert.message,
      metadata: alert.metadata ? decryptJSON(alert.metadata) : null,
      status: alert.status,
      sentAt: alert.sentAt,
      readAt: alert.readAt,
      acknowledgedAt: alert.acknowledgedAt,
      sender: alert.sender
    }));

  } catch (error) {
    logger.error('Failed to get alerts for user', { userId, error });
    throw error;
  }
};

/**
 * Mark alert as read
 */
export const markAlertAsRead = async (alertId: string, userId: string): Promise<void> => {
  try {
    const alert = await db.alert.findFirst({
      where: {
        id: alertId,
        receiverId: userId
      }
    });

    if (!alert) {
      throw new Error('Alert not found');
    }

    await db.alert.update({
      where: { id: alertId },
      data: {
        status: AlertStatus.READ,
        readAt: new Date()
      }
    });

    logger.info('Alert marked as read', { alertId, userId });

  } catch (error) {
    logger.error('Failed to mark alert as read', { alertId, userId, error });
    throw error;
  }
};

/**
 * Acknowledge alert (family member has taken action)
 */
export const acknowledgeAlert = async (alertId: string, userId: string): Promise<void> => {
  try {
    const alert = await db.alert.findFirst({
      where: {
        id: alertId,
        receiverId: userId
      }
    });

    if (!alert) {
      throw new Error('Alert not found');
    }

    await db.alert.update({
      where: { id: alertId },
      data: {
        status: AlertStatus.ACKNOWLEDGED,
        acknowledgedAt: new Date()
      }
    });

    logger.info('Alert acknowledged', { alertId, userId });

  } catch (error) {
    logger.error('Failed to acknowledge alert', { alertId, userId, error });
    throw error;
  }
};

/**
 * Send daily activity summary to family members
 */
export const sendDailySummary = async (seniorId: string): Promise<void> => {
  try {
    const today = new Date();
    const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000);

    // Get activity summary for yesterday
    const callCount = await db.callLog.count({
      where: {
        userId: seniorId,
        timestamp: {
          gte: yesterday,
          lt: today
        }
      }
    });

    const smsCount = await db.smsLog.aggregate({
      where: {
        userId: seniorId,
        timestamp: {
          gte: yesterday,
          lt: today
        }
      },
      _sum: {
        messageCount: true
      }
    });

    const summary = {
      date: yesterday.toISOString().split('T')[0],
      totalCalls: callCount,
      totalSms: smsCount._sum.messageCount || 0
    };

    // Send summary alert to family members
    await createAlert({
      senderId: seniorId,
      type: AlertType.FAMILY_MESSAGE,
      severity: AlertSeverity.LOW,
      title: 'Daily Activity Summary',
      message: `Yesterday: ${summary.totalCalls} calls, ${summary.totalSms} messages`,
      metadata: encryptJSON(summary)
    });

    logger.info('Daily summary sent', { seniorId, summary });

  } catch (error) {
    logger.error('Failed to send daily summary', { seniorId, error });
    throw error;
  }
};

/**
 * Clean up old alerts based on retention policy
 */
export const cleanupOldAlerts = async (): Promise<void> => {
  try {
    const retentionDate = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000); // 90 days

    const deletedAlerts = await db.alert.deleteMany({
      where: {
        sentAt: { lt: retentionDate }
      }
    });

    logger.info('Old alerts cleaned up', {
      deletedCount: deletedAlerts.count,
      retentionDate
    });

  } catch (error) {
    logger.error('Failed to cleanup old alerts', { error });
    throw error;
  }
};

// Export service object
export const notificationService = {
  createAlert,
  getAlertsForUser,
  markAlertAsRead,
  acknowledgeAlert,
  sendDailySummary,
  cleanupOldAlerts
}; 