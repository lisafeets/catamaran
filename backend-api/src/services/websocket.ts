import { WebSocketServer } from 'ws';
import { logger } from '@/utils/logger';

export const setupWebSocket = (wss: WebSocketServer) => {
  wss.on('connection', (ws, req) => {
    logger.info('WebSocket connection established', {
      ip: req.socket.remoteAddress,
      userAgent: req.headers['user-agent'],
    });

    ws.on('message', (message) => {
      logger.info('WebSocket message received', { message: message.toString() });
    });

    ws.on('close', () => {
      logger.info('WebSocket connection closed');
    });

    ws.on('error', (error) => {
      logger.error('WebSocket error', { error: error.message });
    });

    // Send welcome message
    ws.send(JSON.stringify({
      type: 'welcome',
      message: 'Connected to Catamaran real-time notifications',
      timestamp: new Date().toISOString(),
    }));
  });

  logger.info('WebSocket server setup complete');
}; 