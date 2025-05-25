import bcrypt from 'bcrypt';
import jwt from 'jsonwebtoken';
import { UserRole } from '@prisma/client';
import { db } from '@/database/prisma';
import { env } from '@/utils/validateEnv';
import { logger } from '@/utils/logger';
import { generateSecureToken, encryptJSON } from '@/utils/encryption';

interface RegisterUserInput {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
  role: UserRole;
}

interface LoginInput {
  email: string;
  password: string;
}

interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

interface AuthResponse {
  user: {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    role: UserRole;
    emailVerified: boolean;
  };
  tokens: TokenPair;
}

/**
 * Register a new user (senior or family member)
 */
export const registerUser = async (input: RegisterUserInput): Promise<AuthResponse> => {
  const { email, password, firstName, lastName, phone, role } = input;

  try {
    // Check if user already exists
    const existingUser = await db.user.findUnique({
      where: { email: email.toLowerCase() }
    });

    if (existingUser) {
      throw new Error('User with this email already exists');
    }

    // Check phone number uniqueness if provided
    if (phone) {
      const existingPhone = await db.user.findUnique({
        where: { phone }
      });

      if (existingPhone) {
        throw new Error('User with this phone number already exists');
      }
    }

    // Hash password
    const hashedPassword = await bcrypt.hash(password, env.BCRYPT_ROUNDS);

    // Create user in transaction
    const user = await db.$transaction(async (tx) => {
      // Create user
      const newUser = await tx.user.create({
        data: {
          email: email.toLowerCase(),
          password: hashedPassword,
          firstName,
          lastName,
          phone,
          role,
          consentGivenAt: new Date(),
          privacyPolicyVersion: '1.0'
        }
      });

      // Create role-specific profile
      if (role === UserRole.SENIOR) {
        await tx.seniorProfile.create({
          data: {
            userId: newUser.id,
            emergencyContacts: encryptJSON([]), // Empty array, to be filled later
          }
        });
      } else if (role === UserRole.FAMILY_MEMBER) {
        await tx.familyProfile.create({
          data: {
            userId: newUser.id,
          }
        });
      }

      return newUser;
    });

    // Generate tokens
    const tokens = await generateTokenPair(user.id, user.email, user.role);

    // Log successful registration
    logger.info('User registered successfully', {
      userId: user.id,
      email: user.email,
      role: user.role
    });

    return {
      user: {
        id: user.id,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
        role: user.role,
        emailVerified: user.emailVerified
      },
      tokens
    };

  } catch (error) {
    logger.error('User registration failed', { email, error });
    throw error;
  }
};

/**
 * Authenticate user login
 */
export const loginUser = async (input: LoginInput): Promise<AuthResponse> => {
  const { email, password } = input;

  try {
    // Find user with profiles
    const user = await db.user.findUnique({
      where: { 
        email: email.toLowerCase(),
        isActive: true 
      },
      include: {
        seniorProfile: true,
        familyProfile: true
      }
    });

    if (!user) {
      throw new Error('Invalid email or password');
    }

    // Verify password
    const isPasswordValid = await bcrypt.compare(password, user.password);
    if (!isPasswordValid) {
      throw new Error('Invalid email or password');
    }

    // Update last seen
    await db.user.update({
      where: { id: user.id },
      data: { lastSeenAt: new Date() }
    });

    // Update senior online status
    if (user.seniorProfile) {
      await db.seniorProfile.update({
        where: { userId: user.id },
        data: { 
          isOnline: true,
          lastHeartbeat: new Date()
        }
      });
    }

    // Generate tokens
    const tokens = await generateTokenPair(user.id, user.email, user.role);

    // Log successful login
    logger.info('User logged in successfully', {
      userId: user.id,
      email: user.email,
      role: user.role
    });

    return {
      user: {
        id: user.id,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
        role: user.role,
        emailVerified: user.emailVerified
      },
      tokens
    };

  } catch (error) {
    logger.error('User login failed', { email, error });
    throw error;
  }
};

/**
 * Refresh access token using refresh token
 */
export const refreshAccessToken = async (refreshToken: string): Promise<TokenPair> => {
  try {
    // Verify refresh token
    const decoded = jwt.verify(refreshToken, env.JWT_REFRESH_SECRET) as any;
    
    // Check if user exists and is active
    const user = await db.user.findUnique({
      where: { 
        id: decoded.id,
        isActive: true 
      }
    });

    if (!user) {
      throw new Error('User not found or inactive');
    }

    // Generate new token pair
    const tokens = await generateTokenPair(user.id, user.email, user.role);

    logger.info('Access token refreshed', { userId: user.id });

    return tokens;

  } catch (error) {
    logger.error('Token refresh failed', { error });
    throw new Error('Invalid refresh token');
  }
};

/**
 * Logout user (invalidate tokens)
 */
export const logoutUser = async (userId: string): Promise<void> => {
  try {
    // Update senior offline status
    const user = await db.user.findUnique({
      where: { id: userId },
      include: { seniorProfile: true }
    });

    if (user?.seniorProfile) {
      await db.seniorProfile.update({
        where: { userId },
        data: { isOnline: false }
      });
    }

    logger.info('User logged out', { userId });

  } catch (error) {
    logger.error('Logout failed', { userId, error });
    throw error;
  }
};

/**
 * Change user password
 */
export const changePassword = async (
  userId: string, 
  currentPassword: string, 
  newPassword: string
): Promise<void> => {
  try {
    // Get user
    const user = await db.user.findUnique({
      where: { id: userId }
    });

    if (!user) {
      throw new Error('User not found');
    }

    // Verify current password
    const isCurrentPasswordValid = await bcrypt.compare(currentPassword, user.password);
    if (!isCurrentPasswordValid) {
      throw new Error('Current password is incorrect');
    }

    // Hash new password
    const hashedNewPassword = await bcrypt.hash(newPassword, env.BCRYPT_ROUNDS);

    // Update password
    await db.user.update({
      where: { id: userId },
      data: { password: hashedNewPassword }
    });

    logger.info('Password changed successfully', { userId });

  } catch (error) {
    logger.error('Password change failed', { userId, error });
    throw error;
  }
};

/**
 * Request password reset
 */
export const requestPasswordReset = async (email: string): Promise<string> => {
  try {
    const user = await db.user.findUnique({
      where: { email: email.toLowerCase() }
    });

    if (!user) {
      // Don't reveal if email exists
      logger.info('Password reset requested for non-existent email', { email });
      return 'If an account with this email exists, a reset link will be sent.';
    }

    // Generate reset token (valid for 1 hour)
    const resetToken = generateSecureToken(32);
    
    // In production, store reset token in database and send email
    // For now, just log it
    logger.info('Password reset requested', { 
      userId: user.id, 
      email: user.email,
      resetToken 
    });

    return 'If an account with this email exists, a reset link will be sent.';

  } catch (error) {
    logger.error('Password reset request failed', { email, error });
    throw error;
  }
};

/**
 * Generate JWT token pair
 */
const generateTokenPair = async (
  userId: string, 
  email: string, 
  role: UserRole
): Promise<TokenPair> => {
  const payload = { id: userId, email, role };

  const accessToken = jwt.sign(
    payload,
    env.JWT_SECRET,
    { expiresIn: env.JWT_ACCESS_EXPIRY }
  );

  const refreshToken = jwt.sign(
    payload,
    env.JWT_REFRESH_SECRET,
    { expiresIn: env.JWT_REFRESH_EXPIRY }
  );

  return { accessToken, refreshToken };
};

/**
 * Verify JWT token
 */
export const verifyToken = (token: string, isRefresh: boolean = false): any => {
  const secret = isRefresh ? env.JWT_REFRESH_SECRET : env.JWT_SECRET;
  return jwt.verify(token, secret);
}; 