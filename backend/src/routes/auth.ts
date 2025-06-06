import express from 'express';
import bcrypt from 'bcrypt';
import jwt from 'jsonwebtoken';
import { body, validationResult } from 'express-validator';
import { prisma } from '../server';
import { ApiError, asyncHandler } from '../middleware/errorHandler';
import { authenticateToken } from '../middleware/auth';
import logger from '../utils/logger';
import { Router, Request, Response } from 'express';

const router = express.Router();

// Validation rules
const registerValidation = [
  body('email')
    .isEmail()
    .normalizeEmail()
    .withMessage('Valid email is required'),
  body('password')
    .isLength({ min: 8 })
    .withMessage('Password must be at least 8 characters')
    .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/)
    .withMessage('Password must contain at least one uppercase letter, one lowercase letter, and one number'),
  body('firstName')
    .optional()
    .isLength({ min: 1, max: 50 })
    .withMessage('First name must be 1-50 characters'),
  body('lastName')
    .optional()
    .isLength({ min: 1, max: 50 })
    .withMessage('Last name must be 1-50 characters'),
  body('phone')
    .optional()
    .isMobilePhone('any')
    .withMessage('Valid phone number is required'),
  body('role')
    .isIn(['SENIOR', 'FAMILY_MEMBER'])
    .withMessage('Role must be SENIOR or FAMILY_MEMBER')
];

const loginValidation = [
  body('email')
    .isEmail()
    .normalizeEmail()
    .withMessage('Valid email is required'),
  body('password')
    .notEmpty()
    .withMessage('Password is required')
];

// Helper function to generate JWT tokens
const generateTokens = (user: { id: string; email: string; role: string }) => {
  // Fallback secrets for Railway deployment if not configured
  const JWT_SECRET = process.env.JWT_SECRET || 'catamaran-default-jwt-secret-for-testing-only-change-in-production';
  const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'catamaran-default-refresh-secret-for-testing-only-change-in-production';
  
  const accessToken = jwt.sign(
    { id: user.id, email: user.email, role: user.role },
    JWT_SECRET,
    { expiresIn: '15m' }
  );

  const refreshToken = jwt.sign(
    { id: user.id, email: user.email, role: user.role },
    JWT_REFRESH_SECRET,
    { expiresIn: '7d' }
  );

  return { accessToken, refreshToken };
};

// POST /api/auth/register
router.post('/register', registerValidation, asyncHandler(async (req, res) => {
  // Check validation errors
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    throw new ApiError('Validation failed', 400);
  }

  const { email, password, firstName, lastName, phone, role } = req.body;

  // Check if user already exists
  const existingUser = await prisma.user.findUnique({
    where: { email }
  });

  if (existingUser) {
    throw new ApiError('User already exists with this email', 409);
  }

  // Hash password
  const saltRounds = parseInt(process.env.BCRYPT_ROUNDS || '12');
  const hashedPassword = await bcrypt.hash(password, saltRounds);

  // Create user
  const user = await prisma.user.create({
    data: {
      email,
      password: hashedPassword,
      firstName,
      lastName,
      phone,
      role,
      emailVerified: false // In production, implement email verification
    },
    select: {
      id: true,
      email: true,
      firstName: true,
      lastName: true,
      phone: true,
      role: true,
      createdAt: true
    }
  });

  // Generate tokens
  const tokens = generateTokens(user);

  // Store refresh token
  const refreshTokenExpiry = new Date();
  refreshTokenExpiry.setDate(refreshTokenExpiry.getDate() + 7); // 7 days

  await prisma.refreshToken.create({
    data: {
      token: tokens.refreshToken,
      userId: user.id,
      expiresAt: refreshTokenExpiry
    }
  });

  logger.info('User registered successfully', { userId: user.id, email: user.email, role: user.role });

  res.status(201).json({
    success: true,
    message: 'User registered successfully',
    user,
    tokens
  });
}));

// POST /api/auth/login
router.post('/login', loginValidation, asyncHandler(async (req, res) => {
  // Check validation errors
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    throw new ApiError('Validation failed', 400);
  }

  const { email, password } = req.body;

  // Find user
  const user = await prisma.user.findUnique({
    where: { email }
  });

  if (!user || !user.isActive) {
    throw new ApiError('Invalid credentials', 401);
  }

  // Check password
  const isPasswordValid = await bcrypt.compare(password, user.password);
  if (!isPasswordValid) {
    throw new ApiError('Invalid credentials', 401);
  }

  // Generate tokens
  const tokens = generateTokens(user);

  // Store refresh token
  const refreshTokenExpiry = new Date();
  refreshTokenExpiry.setDate(refreshTokenExpiry.getDate() + 7);

  await prisma.refreshToken.create({
    data: {
      token: tokens.refreshToken,
      userId: user.id,
      expiresAt: refreshTokenExpiry
    }
  });

  // Update last login
  await prisma.user.update({
    where: { id: user.id },
    data: { lastLoginAt: new Date() }
  });

  logger.info('User logged in successfully', { userId: user.id, email: user.email });

  res.json({
    success: true,
    message: 'Login successful',
    user: {
      id: user.id,
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      phone: user.phone,
      role: user.role
    },
    tokens
  });
}));

// POST /api/auth/refresh
router.post('/refresh', asyncHandler(async (req, res) => {
  const { refreshToken } = req.body;

  if (!refreshToken) {
    throw new ApiError('Refresh token required', 401);
  }

  // Verify refresh token
  const decoded = jwt.verify(refreshToken, process.env.JWT_REFRESH_SECRET!) as any;

  // Check if refresh token exists in database and is not revoked
  const storedToken = await prisma.refreshToken.findFirst({
    where: {
      token: refreshToken,
      userId: decoded.id,
      isRevoked: false,
      expiresAt: {
        gt: new Date()
      }
    },
    include: {
      user: {
        select: {
          id: true,
          email: true,
          role: true,
          isActive: true
        }
      }
    }
  });

  if (!storedToken || !storedToken.user.isActive) {
    throw new ApiError('Invalid refresh token', 401);
  }

  // Generate new tokens
  const tokens = generateTokens(storedToken.user);

  // Revoke old refresh token
  await prisma.refreshToken.update({
    where: { id: storedToken.id },
    data: { isRevoked: true }
  });

  // Store new refresh token
  const refreshTokenExpiry = new Date();
  refreshTokenExpiry.setDate(refreshTokenExpiry.getDate() + 7);

  await prisma.refreshToken.create({
    data: {
      token: tokens.refreshToken,
      userId: storedToken.user.id,
      expiresAt: refreshTokenExpiry
    }
  });

  res.json({
    success: true,
    tokens
  });
}));

// POST /api/auth/logout
router.post('/logout', authenticateToken, asyncHandler(async (req, res) => {
  const { refreshToken } = req.body;

  if (refreshToken) {
    // Revoke refresh token
    await prisma.refreshToken.updateMany({
      where: {
        token: refreshToken,
        userId: req.user!.id
      },
      data: {
        isRevoked: true
      }
    });
  }

  // Revoke all refresh tokens for the user (optional - for logout from all devices)
  if (req.body.logoutAll) {
    await prisma.refreshToken.updateMany({
      where: {
        userId: req.user!.id,
        isRevoked: false
      },
      data: {
        isRevoked: true
      }
    });
  }

  logger.info('User logged out', { userId: req.user!.id });

  res.json({
    success: true,
    message: 'Logged out successfully'
  });
}));

// GET /api/auth/me
router.get('/me', authenticateToken, asyncHandler(async (req, res) => {
  const user = await prisma.user.findUnique({
    where: { id: req.user!.id },
    select: {
      id: true,
      email: true,
      firstName: true,
      lastName: true,
      phone: true,
      role: true,
      createdAt: true,
      lastLoginAt: true
    }
  });

  if (!user) {
    throw new ApiError('User not found', 404);
  }

  res.json({
    success: true,
    user
  });
}));

// Generate test token endpoint (for testing only)
router.post('/token', async (req: Request, res: Response) => {
  try {
    const jwtSecret = process.env.JWT_SECRET || 'catamaran-default-jwt-secret-for-testing-only-change-in-production';
    const now = Math.floor(Date.now() / 1000);
    const sixMonthsFromNow = now + (6 * 30 * 24 * 60 * 60); // 6 months in seconds

    const payload = {
      id: 'test-user-123',
      email: 'test@example.com',
      role: 'SENIOR',
      iat: now,
      exp: sixMonthsFromNow
    };

    const token = jwt.sign(payload, jwtSecret);

    res.json({
      success: true,
      token: token,
      expiresAt: new Date(sixMonthsFromNow * 1000).toISOString(),
      payload: payload
    });

    logger.info('Generated test token', { expiresAt: new Date(sixMonthsFromNow * 1000).toISOString() });
  } catch (error) {
    logger.error('Error generating test token', error);
    res.status(500).json({
      success: false,
      message: 'Failed to generate token'
    });
  }
});

export default router; 