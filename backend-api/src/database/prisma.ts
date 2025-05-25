import { PrismaClient } from '@prisma/client';
import { logger } from '@/utils/logger';
import { env } from '@/utils/validateEnv';

// Create a singleton Prisma client
let prisma: PrismaClient;

declare global {
  var __prisma: PrismaClient | undefined;
}

/**
 * Initialize and configure Prisma client
 */
const createPrismaClient = (): PrismaClient => {
  const client = new PrismaClient({
    datasources: {
      db: {
        url: env.DATABASE_URL,
      },
    },
    log: env.NODE_ENV === 'development' 
      ? ['query', 'info', 'warn', 'error']
      : ['warn', 'error'],
    errorFormat: 'pretty',
  });

  // Add logging middleware
  client.$use(async (params, next) => {
    const start = Date.now();
    const result = await next(params);
    const duration = Date.now() - start;
    
    logger.debug('Database query executed', {
      model: params.model,
      action: params.action,
      duration: `${duration}ms`,
    });
    
    return result;
  });

  return client;
};

/**
 * Get or create Prisma client instance
 * Uses singleton pattern to prevent multiple connections
 */
export const getPrismaClient = (): PrismaClient => {
  if (env.NODE_ENV === 'production') {
    if (!prisma) {
      prisma = createPrismaClient();
    }
    return prisma;
  } else {
    // In development, use global variable to prevent hot reload issues
    if (!global.__prisma) {
      global.__prisma = createPrismaClient();
    }
    return global.__prisma;
  }
};

/**
 * Connect to database
 */
export const connectDatabase = async (): Promise<void> => {
  try {
    const client = getPrismaClient();
    await client.$connect();
    logger.info('✅ Database connected successfully');
  } catch (error) {
    logger.error('❌ Database connection failed:', error);
    throw error;
  }
};

/**
 * Disconnect from database
 */
export const disconnectDatabase = async (): Promise<void> => {
  try {
    const client = getPrismaClient();
    await client.$disconnect();
    logger.info('Database disconnected');
  } catch (error) {
    logger.error('Error disconnecting from database:', error);
    throw error;
  }
};

/**
 * Check database health
 */
export const checkDatabaseHealth = async (): Promise<boolean> => {
  try {
    const client = getPrismaClient();
    await client.$queryRaw`SELECT 1`;
    return true;
  } catch (error) {
    logger.error('Database health check failed:', error);
    return false;
  }
};

/**
 * Execute database transaction
 * @param operations - Array of operations to execute in transaction
 */
export const executeTransaction = async (
  operations: ((client: PrismaClient) => Promise<any>)[]
): Promise<any[]> => {
  const client = getPrismaClient();
  
  return client.$transaction(async (tx) => {
    const results = [];
    for (const operation of operations) {
      const result = await operation(tx);
      results.push(result);
    }
    return results;
  });
};

// Export the default client instance
export const db = getPrismaClient(); 