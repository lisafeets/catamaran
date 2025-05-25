import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';
import { PrismaClient } from '@prisma/client';

// Load environment variables first
dotenv.config();

console.log('🚀 Starting Catamaran Backend Server...');
console.log('📋 Environment Check:');
console.log('  - NODE_ENV:', process.env.NODE_ENV || 'not set');
console.log('  - PORT:', process.env.PORT || '3001 (default)');
console.log('  - DATABASE_URL:', process.env.DATABASE_URL ? '✅ Set' : '❌ Missing');
console.log('  - JWT_SECRET:', process.env.JWT_SECRET ? '✅ Set' : '❌ Missing');

const app = express();
let prisma: PrismaClient;

// Initialize Prisma with error handling
try {
  console.log('📊 Initializing Prisma Client...');
  prisma = new PrismaClient({
    log: ['info', 'warn', 'error'],
  });
  console.log('✅ Prisma Client initialized');
} catch (error) {
  console.error('❌ Failed to initialize Prisma Client:', error);
  process.exit(1);
}

// Validate critical environment variables
const requiredEnvVars = ['DATABASE_URL', 'JWT_SECRET'];
const missingEnvVars = requiredEnvVars.filter(envVar => !process.env[envVar]);

if (missingEnvVars.length > 0) {
  console.error(`❌ Missing required environment variables: ${missingEnvVars.join(', ')}`);
  process.exit(1);
}

// Test database connection early
async function testDatabaseConnection() {
  try {
    console.log('🔌 Testing database connection...');
    await prisma.$connect();
    await prisma.$queryRaw`SELECT 1`;
    console.log('✅ Database connection successful');
    return true;
  } catch (error) {
    console.error('❌ Database connection failed:', error);
    return false;
  }
}

// Middleware setup with error handling
try {
  console.log('⚙️ Setting up middleware...');

  // Security middleware
  app.use(helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        styleSrc: ["'self'", "'unsafe-inline'"],
        scriptSrc: ["'self'"],
        imgSrc: ["'self'", "data:", "https:"],
      },
    },
  }));

  app.use(compression());

  // CORS configuration
  app.use(cors({
    origin: process.env.CORS_ORIGIN?.split(',') || ['http://localhost:3000', 'http://localhost:5173'],
    credentials: true,
    optionsSuccessStatus: 200
  }));

  // Rate limiting
  const limiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 100, // limit each IP to 100 requests per windowMs
    message: {
      error: 'Too many requests from this IP, please try again later.',
    },
    standardHeaders: true,
    legacyHeaders: false,
  });

  app.use(limiter);

  // Body parser middleware
  app.use(express.json({ limit: '10mb' }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));

  console.log('✅ Middleware setup complete');
} catch (error) {
  console.error('❌ Middleware setup failed:', error);
  process.exit(1);
}

// Request logging middleware
app.use((req, res, next) => {
  console.log(`📝 ${req.method} ${req.path} - ${new Date().toISOString()}`);
  next();
});

// Health check endpoint
app.get('/health', async (req, res) => {
  try {
    console.log('🏥 Health check requested');
    
    // Test database connection
    await prisma.$queryRaw`SELECT 1`;
    
    const healthStatus = {
      status: 'healthy',
      timestamp: new Date().toISOString(),
      version: '1.0.0',
      environment: process.env.NODE_ENV || 'development',
      database: 'connected'
    };
    
    console.log('✅ Health check passed');
    res.status(200).json(healthStatus);
  } catch (error) {
    console.error('❌ Health check failed:', error);
    res.status(503).json({
      status: 'unhealthy',
      timestamp: new Date().toISOString(),
      error: 'Database connection failed',
      details: error instanceof Error ? error.message : 'Unknown error'
    });
  }
});

// Basic API routes (simplified for debugging)
app.get('/api/test', (req, res) => {
  console.log('🧪 Test endpoint hit');
  res.json({ 
    message: 'Catamaran API is working!',
    timestamp: new Date().toISOString() 
  });
});

// Import and use routes with error handling
try {
  console.log('🛣️ Loading API routes...');
  
  // Dynamically import routes with error handling
  Promise.all([
    import('./routes/auth').catch(err => {
      console.warn('⚠️ Auth routes not found, skipping...', err.message);
      return null;
    }),
    import('./routes/activity').catch(err => {
      console.warn('⚠️ Activity routes not found, skipping...', err.message);
      return null;
    }),
    import('./routes/family').catch(err => {
      console.warn('⚠️ Family routes not found, skipping...', err.message);
      return null;
    })
  ]).then(([authRoutes, activityRoutes, familyRoutes]) => {
    if (authRoutes?.default) app.use('/api/auth', authRoutes.default);
    if (activityRoutes?.default) app.use('/api/activity', activityRoutes.default);
    if (familyRoutes?.default) app.use('/api/family', familyRoutes.default);
    
    console.log('✅ API routes loaded');
  }).catch(error => {
    console.error('❌ Failed to load routes:', error);
  });

} catch (error) {
  console.error('❌ Route setup failed:', error);
  // Don't exit - continue with basic server
}

// 404 handler
app.use('*', (req, res) => {
  console.log(`❓ 404: ${req.method} ${req.originalUrl}`);
  res.status(404).json({
    error: 'Route not found',
    path: req.originalUrl,
    method: req.method,
    availableEndpoints: ['/health', '/api/test']
  });
});

// Global error handling middleware
app.use((error: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
  console.error('💥 Global error handler:', error);
  
  res.status(error.status || 500).json({
    error: 'Internal server error',
    message: error.message || 'Something went wrong',
    timestamp: new Date().toISOString()
  });
});

// Handle unhandled promise rejections
process.on('unhandledRejection', (reason, promise) => {
  console.error('💥 Unhandled Rejection at:', promise);
  console.error('💥 Reason:', reason);
  // Don't exit in production, just log
  if (process.env.NODE_ENV !== 'production') {
    process.exit(1);
  }
});

// Handle uncaught exceptions
process.on('uncaughtException', (error) => {
  console.error('💥 Uncaught Exception:', error);
  process.exit(1);
});

// Graceful shutdown
const gracefulShutdown = async (signal: string) => {
  console.log(`🛑 Received ${signal}, shutting down gracefully...`);
  
  try {
    if (prisma) {
      await prisma.$disconnect();
      console.log('✅ Database connection closed');
    }
    process.exit(0);
  } catch (error) {
    console.error('❌ Error during shutdown:', error);
    process.exit(1);
  }
};

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

// Start server with comprehensive error handling
async function startServer() {
  try {
    // Test database connection before starting server
    const dbConnected = await testDatabaseConnection();
    if (!dbConnected) {
      console.error('❌ Cannot start server without database connection');
      process.exit(1);
    }

    const PORT = process.env.PORT || 3001;
    
    const server = app.listen(PORT, () => {
      console.log('🎉 ================================');
      console.log('🎉 CATAMARAN BACKEND STARTED!');
      console.log('🎉 ================================');
      console.log(`🚀 Server running on port ${PORT}`);
      console.log(`🌐 Health check: http://localhost:${PORT}/health`);
      console.log(`🧪 Test endpoint: http://localhost:${PORT}/api/test`);
      console.log(`📊 Environment: ${process.env.NODE_ENV || 'development'}`);
      console.log('🎉 ================================');
    });

    server.on('error', (error: any) => {
      if (error.code === 'EADDRINUSE') {
        console.error(`❌ Port ${PORT} is already in use`);
      } else {
        console.error('❌ Server error:', error);
      }
      process.exit(1);
    });

  } catch (error) {
    console.error('❌ Failed to start server:', error);
    process.exit(1);
  }
}

// Start the server
startServer().catch(error => {
  console.error('💥 Fatal startup error:', error);
  process.exit(1);
});

export { app, prisma };