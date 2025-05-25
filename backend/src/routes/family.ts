import express from 'express';
import { body, validationResult, param } from 'express-validator';
import { prisma } from '../server';
import { ApiError, asyncHandler } from '../middleware/errorHandler';
import { authenticateToken } from '../middleware/auth';
import logger from '../utils/logger';

const router = express.Router();

// Validation for family connection
const connectValidation = [
  body('seniorEmail')
    .isEmail()
    .normalizeEmail()
    .withMessage('Valid senior email is required'),
  body('relationship')
    .isIn(['CHILD', 'SPOUSE', 'PARENT', 'SIBLING', 'CAREGIVER', 'OTHER'])
    .withMessage('Valid relationship type is required')
];

// POST /api/family/connect - Link senior to family member
router.post('/connect', authenticateToken, connectValidation, asyncHandler(async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    throw new ApiError('Validation failed', 400);
  }

  const { seniorEmail, relationship } = req.body;
  const familyMemberId = req.user!.id;

  // Check if requesting user is a family member
  if (req.user!.role !== 'FAMILY_MEMBER') {
    throw new ApiError('Only family members can initiate connections', 403);
  }

  // Find senior by email
  const senior = await prisma.user.findUnique({
    where: { 
      email: seniorEmail,
      isActive: true,
      role: 'SENIOR'
    }
  });

  if (!senior) {
    throw new ApiError('Senior not found or not active', 404);
  }

  // Check if connection already exists
  const existingConnection = await prisma.familyConnection.findUnique({
    where: {
      seniorId_familyId: {
        seniorId: senior.id,
        familyId: familyMemberId
      }
    }
  });

  if (existingConnection) {
    if (existingConnection.isActive) {
      throw new ApiError('Family connection already exists', 409);
    } else {
      // Reactivate existing connection
      const updatedConnection = await prisma.familyConnection.update({
        where: { id: existingConnection.id },
        data: {
          isActive: true,
          relationship,
          updatedAt: new Date()
        },
        include: {
          senior: {
            select: {
              id: true,
              firstName: true,
              lastName: true,
              email: true
            }
          }
        }
      });

      logger.info('Family connection reactivated', {
        seniorId: senior.id,
        familyId: familyMemberId,
        relationship
      });

      return res.json({
        success: true,
        message: 'Family connection reactivated successfully',
        connection: updatedConnection
      });
    }
  }

  // Create new family connection
  const connection = await prisma.familyConnection.create({
    data: {
      seniorId: senior.id,
      familyId: familyMemberId,
      relationship,
      isActive: true
    },
    include: {
      senior: {
        select: {
          id: true,
          firstName: true,
          lastName: true,
          email: true
        }
      }
    }
  });

  logger.info('New family connection created', {
    seniorId: senior.id,
    familyId: familyMemberId,
    relationship,
    connectionId: connection.id
  });

  res.status(201).json({
    success: true,
    message: 'Family connection created successfully',
    connection
  });
}));

// GET /api/family/connections - Get all family connections for the current user
router.get('/connections', authenticateToken, asyncHandler(async (req, res) => {
  const userId = req.user!.id;
  const userRole = req.user!.role;

  let connections;

  if (userRole === 'SENIOR') {
    // Get family members connected to this senior
    connections = await prisma.familyConnection.findMany({
      where: {
        seniorId: userId,
        isActive: true
      },
      include: {
        family: {
          select: {
            id: true,
            firstName: true,
            lastName: true,
            email: true,
            phone: true
          }
        }
      },
      orderBy: {
        connectedAt: 'desc'
      }
    });
  } else {
    // Get seniors this family member is connected to
    connections = await prisma.familyConnection.findMany({
      where: {
        familyId: userId,
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
      },
      orderBy: {
        connectedAt: 'desc'
      }
    });
  }

  res.json({
    success: true,
    connections,
    count: connections.length
  });
}));

// DELETE /api/family/disconnect/:connectionId - Remove family connection
router.delete('/disconnect/:connectionId', 
  authenticateToken, 
  param('connectionId').isString(),
  asyncHandler(async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      throw new ApiError('Invalid connection ID', 400);
    }

    const { connectionId } = req.params;
    const userId = req.user!.id;

    // Find the connection
    const connection = await prisma.familyConnection.findUnique({
      where: { id: connectionId },
      include: {
        senior: {
          select: { id: true, firstName: true, lastName: true }
        },
        family: {
          select: { id: true, firstName: true, lastName: true }
        }
      }
    });

    if (!connection) {
      throw new ApiError('Connection not found', 404);
    }

    // Check if user is authorized to disconnect
    const isAuthorized = connection.seniorId === userId || connection.familyId === userId;
    
    if (!isAuthorized) {
      throw new ApiError('Not authorized to disconnect this family connection', 403);
    }

    // Deactivate the connection (soft delete)
    await prisma.familyConnection.update({
      where: { id: connectionId },
      data: {
        isActive: false,
        updatedAt: new Date()
      }
    });

    logger.info('Family connection disconnected', {
      connectionId,
      seniorId: connection.seniorId,
      familyId: connection.familyId,
      disconnectedBy: userId
    });

    res.json({
      success: true,
      message: 'Family connection removed successfully'
    });
  })
);

// GET /api/family/seniors - Get list of seniors for family member dashboard
router.get('/seniors', authenticateToken, asyncHandler(async (req, res) => {
  const userId = req.user!.id;

  if (req.user!.role !== 'FAMILY_MEMBER') {
    throw new ApiError('Only family members can access this endpoint', 403);
  }

  // Get all seniors connected to this family member
  const connections = await prisma.familyConnection.findMany({
    where: {
      familyId: userId,
      isActive: true
    },
    include: {
      senior: {
        select: {
          id: true,
          firstName: true,
          lastName: true,
          email: true,
          phone: true,
          lastLoginAt: true
        }
      }
    }
  });

  // Get recent activity summary for each senior
  const seniorsWithActivity = await Promise.all(
    connections.map(async (connection) => {
      const seniorId = connection.senior.id;
      
      // Get activity count from last 24 hours
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);

      const [callCount, smsCount, callRiskCount, smsRiskCount] = await Promise.all([
        prisma.callLog.count({
          where: {
            seniorId,
            timestamp: { gte: yesterday }
          }
        }),
        prisma.smsLog.count({
          where: {
            seniorId,
            timestamp: { gte: yesterday }
          }
        }),
        prisma.callLog.count({
          where: {
            seniorId,
            riskScore: { gte: 0.7 },
            timestamp: { gte: yesterday }
          }
        }),
        prisma.smsLog.count({
          where: {
            seniorId,
            riskScore: { gte: 0.7 },
            timestamp: { gte: yesterday }
          }
        })
      ]);

      const highRiskCount = callRiskCount + smsRiskCount;

      return {
        ...connection,
        activitySummary: {
          recentCalls: callCount,
          recentSms: smsCount,
          highRiskAlerts: highRiskCount,
          lastActivity: new Date().toISOString() // This would be the actual last activity timestamp
        }
      };
    })
  );

  res.json({
    success: true,
    seniors: seniorsWithActivity,
    count: seniorsWithActivity.length
  });
}));

// PUT /api/family/relationship/:connectionId - Update relationship type
router.put('/relationship/:connectionId',
  authenticateToken,
  param('connectionId').isString(),
  body('relationship').isIn(['CHILD', 'SPOUSE', 'PARENT', 'SIBLING', 'CAREGIVER', 'OTHER']),
  asyncHandler(async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      throw new ApiError('Validation failed', 400);
    }

    const { connectionId } = req.params;
    const { relationship } = req.body;
    const userId = req.user!.id;

    // Find and update the connection
    const connection = await prisma.familyConnection.findUnique({
      where: { id: connectionId }
    });

    if (!connection) {
      throw new ApiError('Connection not found', 404);
    }

    // Check authorization
    if (connection.familyId !== userId && connection.seniorId !== userId) {
      throw new ApiError('Not authorized to update this connection', 403);
    }

    const updatedConnection = await prisma.familyConnection.update({
      where: { id: connectionId },
      data: { 
        relationship,
        updatedAt: new Date()
      },
      include: {
        senior: {
          select: { id: true, firstName: true, lastName: true }
        },
        family: {
          select: { id: true, firstName: true, lastName: true }
        }
      }
    });

    logger.info('Family relationship updated', {
      connectionId,
      newRelationship: relationship,
      updatedBy: userId
    });

    res.json({
      success: true,
      message: 'Relationship updated successfully',
      connection: updatedConnection
    });
  })
);

export default router; 