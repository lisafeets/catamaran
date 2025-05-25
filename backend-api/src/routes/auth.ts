import { Router, Request, Response } from 'express';
import { body, validationResult } from 'express-validator';
import { UserRole } from '@prisma/client';
import * as authService from '@/services/authService';
import { logger } from '@/utils/logger';
import { authMiddleware } from '@/middleware/auth';

const router = Router();

/**
 * Register new user (senior or family member)
 * POST /api/auth/register
 */
router.post('/register', [
  body('email')
    .isEmail()
    .normalizeEmail()
    .withMessage('Valid email is required'),
  body('password')
    .isLength({ min: 8 })
    .withMessage('Password must be at least 8 characters long')
    .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/)
    .withMessage('Password must contain at least one lowercase letter, one uppercase letter, and one number'),
  body('firstName')
    .trim()
    .isLength({ min: 1, max: 50 })
    .withMessage('First name is required and must be less than 50 characters'),
  body('lastName')
    .trim()
    .isLength({ min: 1, max: 50 })
    .withMessage('Last name is required and must be less than 50 characters'),
  body('phone')
    .optional()
    .isMobilePhone('any')
    .withMessage('Valid phone number is required'),
  body('role')
    .isIn([UserRole.SENIOR, UserRole.FAMILY_MEMBER])
    .withMessage('Role must be either SENIOR or FAMILY_MEMBER')
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

    const { email, password, firstName, lastName, phone, role } = req.body;

    // Register user
    const result = await authService.registerUser({
      email,
      password,
      firstName,
      lastName,
      phone,
      role
    });

    logger.info('User registration successful', { 
      userId: result.user.id, 
      email: result.user.email,
      role: result.user.role 
    });

    res.status(201).json({
      success: true,
      message: 'User registered successfully',
      user: result.user,
      tokens: result.tokens
    });

  } catch (error) {
    logger.error('User registration failed', { 
      email: req.body.email, 
      error: error.message 
    });

    if (error.message.includes('already exists')) {
      return res.status(409).json({
        error: 'User with this email or phone already exists'
      });
    }

    res.status(500).json({
      error: 'Registration failed',
      message: 'An internal error occurred during registration'
    });
  }
});

/**
 * User login
 * POST /api/auth/login
 */
router.post('/login', [
  body('email')
    .isEmail()
    .normalizeEmail()
    .withMessage('Valid email is required'),
  body('password')
    .notEmpty()
    .withMessage('Password is required')
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

    const { email, password } = req.body;

    // Authenticate user
    const result = await authService.loginUser({ email, password });

    logger.info('User login successful', { 
      userId: result.user.id, 
      email: result.user.email,
      role: result.user.role 
    });

    res.json({
      success: true,
      message: 'Login successful',
      user: result.user,
      tokens: result.tokens
    });

  } catch (error) {
    logger.error('User login failed', { 
      email: req.body.email, 
      error: error.message 
    });

    if (error.message.includes('Invalid email or password')) {
      return res.status(401).json({
        error: 'Invalid credentials',
        message: 'Email or password is incorrect'
      });
    }

    res.status(500).json({
      error: 'Login failed',
      message: 'An internal error occurred during login'
    });
  }
});

/**
 * Refresh access token
 * POST /api/auth/refresh
 */
router.post('/refresh', [
  body('refreshToken')
    .notEmpty()
    .withMessage('Refresh token is required')
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

    const { refreshToken } = req.body;

    // Refresh tokens
    const tokens = await authService.refreshAccessToken(refreshToken);

    logger.info('Token refresh successful');

    res.json({
      success: true,
      message: 'Token refreshed successfully',
      tokens
    });

  } catch (error) {
    logger.error('Token refresh failed', { error: error.message });

    res.status(401).json({
      error: 'Token refresh failed',
      message: 'Invalid or expired refresh token'
    });
  }
});

/**
 * User logout
 * POST /api/auth/logout
 */
router.post('/logout', authMiddleware, async (req: Request, res: Response) => {
  try {
    if (!req.user?.id) {
      return res.status(401).json({
        error: 'Authentication required'
      });
    }

    await authService.logoutUser(req.user.id);

    logger.info('User logout successful', { userId: req.user.id });

    res.json({
      success: true,
      message: 'Logout successful'
    });

  } catch (error) {
    logger.error('User logout failed', { 
      userId: req.user?.id, 
      error: error.message 
    });

    res.status(500).json({
      error: 'Logout failed',
      message: 'An internal error occurred during logout'
    });
  }
});

/**
 * Change password
 * POST /api/auth/change-password
 */
router.post('/change-password', [
  authMiddleware,
  body('currentPassword')
    .notEmpty()
    .withMessage('Current password is required'),
  body('newPassword')
    .isLength({ min: 8 })
    .withMessage('New password must be at least 8 characters long')
    .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/)
    .withMessage('New password must contain at least one lowercase letter, one uppercase letter, and one number')
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

    const { currentPassword, newPassword } = req.body;

    await authService.changePassword(req.user.id, currentPassword, newPassword);

    logger.info('Password change successful', { userId: req.user.id });

    res.json({
      success: true,
      message: 'Password changed successfully'
    });

  } catch (error) {
    logger.error('Password change failed', { 
      userId: req.user?.id, 
      error: error.message 
    });

    if (error.message.includes('Current password is incorrect')) {
      return res.status(400).json({
        error: 'Invalid current password',
        message: 'The current password you entered is incorrect'
      });
    }

    res.status(500).json({
      error: 'Password change failed',
      message: 'An internal error occurred while changing password'
    });
  }
});

/**
 * Request password reset
 * POST /api/auth/forgot-password
 */
router.post('/forgot-password', [
  body('email')
    .isEmail()
    .normalizeEmail()
    .withMessage('Valid email is required')
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

    const { email } = req.body;

    const message = await authService.requestPasswordReset(email);

    logger.info('Password reset requested', { email });

    // Always return success to prevent email enumeration
    res.json({
      success: true,
      message
    });

  } catch (error) {
    logger.error('Password reset request failed', { 
      email: req.body.email, 
      error: error.message 
    });

    // Return generic success message to prevent information disclosure
    res.json({
      success: true,
      message: 'If an account with this email exists, a reset link will be sent.'
    });
  }
});

/**
 * Verify user session
 * GET /api/auth/verify
 */
router.get('/verify', authMiddleware, async (req: Request, res: Response) => {
  try {
    if (!req.user) {
      return res.status(401).json({
        error: 'Authentication required'
      });
    }

    res.json({
      success: true,
      user: {
        id: req.user.id,
        email: req.user.email,
        role: req.user.role
      }
    });

  } catch (error) {
    logger.error('Session verification failed', { 
      userId: req.user?.id, 
      error: error.message 
    });

    res.status(500).json({
      error: 'Session verification failed'
    });
  }
});

export default router; 