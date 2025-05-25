import { WebSocket } from 'ws';
import jwt from 'jsonwebtoken';
import { env } from '@/utils/validateEnv';
import { logger } from '@/utils/logger';

interface AuthenticatedWebSocket extends WebSocket {
  userId?: string;
  userRole?: string;
  isAlive?: boolean;
}

interface WebSocketMessage {
  type: string;
  data: any;
}

// Store active connections
const connections = new Map<string, AuthenticatedWebSocket>();
const userConnections = new Map<string, Set<string>>(); // userId -> set of connection IDs

/**
 * Handle new WebSocket connection
 */
export const handleConnection = (ws: AuthenticatedWebSocket, request: any): void => {
  const connectionId = generateConnectionId();
  ws.isAlive = true;

  // Set up ping/pong for connection health
  ws.on('pong', () => {
    ws.isAlive = true;
  });

  // Handle authentication
  ws.on('message', async (data: Buffer) => {
    try {
      const message = JSON.parse(data.toString()) as WebSocketMessage;
      
      if (message.type === 'authenticate') {
        await authenticateConnection(ws, connectionId, message.data.token);
      } else if (ws.userId) {
        await handleMessage(ws, message);
      } else {
        ws.send(JSON.stringify({
          type: 'error',
          message: 'Authentication required'
        }));
      }
    } catch (error) {
      logger.error('WebSocket message error', { connectionId, error });
      ws.send(JSON.stringify({
        type: 'error',
        message: 'Invalid message format'
      }));
    }
  });

  // Handle connection close
  ws.on('close', () => {
    handleDisconnection(connectionId, ws.userId);
  });

  // Handle connection error
  ws.on('error', (error) => {
    logger.error('WebSocket connection error', { connectionId, error });
    handleDisconnection(connectionId, ws.userId);
  });

  // Store connection
  connections.set(connectionId, ws);

  logger.info('New WebSocket connection', { connectionId });
};

/**
 * Authenticate WebSocket connection
 */
const authenticateConnection = async (
  ws: AuthenticatedWebSocket, 
  connectionId: string, 
  token: string
): Promise<void> => {
  try {
    // Verify JWT token
    const decoded = jwt.verify(token, env.JWT_SECRET) as any;
    
    ws.userId = decoded.id;
    ws.userRole = decoded.role;

    // Add to user connections map
    if (!userConnections.has(decoded.id)) {
      userConnections.set(decoded.id, new Set());
    }
    userConnections.get(decoded.id)!.add(connectionId);

    // Send authentication success
    ws.send(JSON.stringify({
      type: 'authenticated',
      data: {
        userId: decoded.id,
        role: decoded.role
      }
    }));

    // Send initial status
    await sendUserStatus(decoded.id);

    logger.info('WebSocket authenticated', { 
      connectionId, 
      userId: decoded.id, 
      role: decoded.role 
    });

  } catch (error) {
    logger.error('WebSocket authentication failed', { connectionId, error });
    
    ws.send(JSON.stringify({
      type: 'authentication_failed',
      message: 'Invalid token'
    }));
    
    ws.close();
  }
};

/**
 * Handle authenticated message
 */
const handleMessage = async (
  ws: AuthenticatedWebSocket, 
  message: WebSocketMessage
): Promise<void> => {
  try {
    switch (message.type) {
      case 'heartbeat':
        ws.send(JSON.stringify({ type: 'heartbeat_ack' }));
        break;
        
      case 'status_update':
        await handleStatusUpdate(ws.userId!, message.data);
        break;
        
      case 'request_activity':
        await sendActivityUpdate(ws.userId!, ws);
        break;
        
      default:
        logger.warn('Unknown WebSocket message type', { 
          type: message.type, 
          userId: ws.userId 
        });
    }
  } catch (error) {
    logger.error('Error handling WebSocket message', { 
      userId: ws.userId, 
      messageType: message.type, 
      error 
    });
  }
};

/**
 * Handle user disconnection
 */
const handleDisconnection = (connectionId: string, userId?: string): void => {
  connections.delete(connectionId);
  
  if (userId) {
    const userConns = userConnections.get(userId);
    if (userConns) {
      userConns.delete(connectionId);
      if (userConns.size === 0) {
        userConnections.delete(userId);
      }
    }
  }

  logger.info('WebSocket disconnected', { connectionId, userId });
};

/**
 * Send message to specific user
 */
export const sendToUser = async (userId: string, message: WebSocketMessage): Promise<void> => {
  const userConns = userConnections.get(userId);
  
  if (!userConns || userConns.size === 0) {
    logger.debug('No active connections for user', { userId });
    return;
  }

  const messageString = JSON.stringify(message);
  const deadConnections: string[] = [];

  for (const connectionId of userConns) {
    const ws = connections.get(connectionId);
    
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      deadConnections.push(connectionId);
      continue;
    }

    try {
      ws.send(messageString);
    } catch (error) {
      logger.error('Failed to send WebSocket message', { 
        connectionId, 
        userId, 
        error 
      });
      deadConnections.push(connectionId);
    }
  }

  // Clean up dead connections
  deadConnections.forEach(connectionId => {
    handleDisconnection(connectionId, userId);
  });
};

/**
 * Broadcast message to all connections
 */
export const broadcast = async (message: WebSocketMessage): Promise<void> => {
  const messageString = JSON.stringify(message);
  const deadConnections: string[] = [];

  for (const [connectionId, ws] of connections) {
    if (ws.readyState !== WebSocket.OPEN) {
      deadConnections.push(connectionId);
      continue;
    }

    try {
      ws.send(messageString);
    } catch (error) {
      logger.error('Failed to broadcast WebSocket message', { 
        connectionId, 
        error 
      });
      deadConnections.push(connectionId);
    }
  }

  // Clean up dead connections
  deadConnections.forEach(connectionId => {
    const ws = connections.get(connectionId);
    handleDisconnection(connectionId, ws?.userId);
  });
};

/**
 * Send family members updates about senior's status
 */
export const notifyFamilyMembers = async (
  seniorId: string, 
  updateType: string, 
  data: any
): Promise<void> => {
  try {
    // In production, get family members from database
    // For now, log the notification
    logger.info('Notifying family members', { 
      seniorId, 
      updateType, 
      data 
    });

    // Send notification to family members
    const message: WebSocketMessage = {
      type: 'family_notification',
      data: {
        seniorId,
        updateType,
        ...data,
        timestamp: new Date().toISOString()
      }
    };

    // In production, get actual family member IDs and send to each
    // await sendToUser(familyMemberId, message);

  } catch (error) {
    logger.error('Failed to notify family members', { 
      seniorId, 
      updateType, 
      error 
    });
  }
};

/**
 * Handle status update from client
 */
const handleStatusUpdate = async (userId: string, data: any): Promise<void> => {
  try {
    logger.info('User status update', { userId, data });
    
    // Update user status in database
    // await db.user.update({
    //   where: { id: userId },
    //   data: { lastSeenAt: new Date() }
    // });

    // Notify family members if this is a senior
    await notifyFamilyMembers(userId, 'status_update', data);

  } catch (error) {
    logger.error('Failed to handle status update', { userId, error });
  }
};

/**
 * Send user their current status
 */
const sendUserStatus = async (userId: string): Promise<void> => {
  try {
    // In production, get user status from database
    const status = {
      online: true,
      lastSeen: new Date().toISOString(),
      // Add other status information
    };

    await sendToUser(userId, {
      type: 'status',
      data: status
    });

  } catch (error) {
    logger.error('Failed to send user status', { userId, error });
  }
};

/**
 * Send activity update to user
 */
const sendActivityUpdate = async (
  userId: string, 
  ws: AuthenticatedWebSocket
): Promise<void> => {
  try {
    // In production, get recent activity from database
    const activity = {
      recentCalls: 0,
      recentSms: 0,
      alerts: 0,
      // Add other activity data
    };

    ws.send(JSON.stringify({
      type: 'activity_update',
      data: activity
    }));

  } catch (error) {
    logger.error('Failed to send activity update', { userId, error });
  }
};

/**
 * Set up periodic ping to keep connections alive
 */
export const setupHeartbeat = (): void => {
  setInterval(() => {
    const deadConnections: string[] = [];

    for (const [connectionId, ws] of connections) {
      if (ws.isAlive === false) {
        deadConnections.push(connectionId);
        ws.terminate();
        continue;
      }

      ws.isAlive = false;
      ws.ping();
    }

    // Clean up dead connections
    deadConnections.forEach(connectionId => {
      const ws = connections.get(connectionId);
      handleDisconnection(connectionId, ws?.userId);
    });

  }, env.WS_HEARTBEAT_INTERVAL);
};

/**
 * Generate unique connection ID
 */
const generateConnectionId = (): string => {
  return `conn_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
};

/**
 * Get connection statistics
 */
export const getConnectionStats = (): {
  totalConnections: number;
  authenticatedConnections: number;
  userConnections: number;
} => {
  const authenticatedConnections = Array.from(connections.values())
    .filter(ws => ws.userId).length;

  return {
    totalConnections: connections.size,
    authenticatedConnections,
    userConnections: userConnections.size
  };
};

// Export service object
export const websocketService = {
  sendToUser,
  broadcast,
  notifyFamilyMembers,
  getConnectionStats
}; 