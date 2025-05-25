import crypto from 'crypto';
import { env } from './validateEnv';
import { logger } from './logger';

interface EncryptedData {
  encryptedData: string;
  iv: string;
  authTag: string;
}

/**
 * Encrypts sensitive data using AES-256-GCM
 * @param data - The data to encrypt (string)
 * @returns Encrypted data object with IV and auth tag
 */
export const encrypt = (data: string): EncryptedData => {
  try {
    // Generate a random initialization vector
    const iv = crypto.randomBytes(16);
    
    // Create cipher
    const cipher = crypto.createCipher(env.ENCRYPTION_ALGORITHM, env.ENCRYPTION_KEY);
    cipher.setAAD(Buffer.from(env.ENCRYPTION_KEY));
    
    // Encrypt the data
    let encryptedData = cipher.update(data, 'utf8', 'hex');
    encryptedData += cipher.final('hex');
    
    // Get the authentication tag
    const authTag = cipher.getAuthTag();
    
    return {
      encryptedData,
      iv: iv.toString('hex'),
      authTag: authTag.toString('hex')
    };
  } catch (error) {
    logger.error('Encryption failed:', error);
    throw new Error('Failed to encrypt data');
  }
};

/**
 * Decrypts data encrypted with the encrypt function
 * @param encryptedData - The encrypted data object
 * @returns The original decrypted string
 */
export const decrypt = (encryptedData: EncryptedData): string => {
  try {
    // Create decipher
    const decipher = crypto.createDecipher(
      env.ENCRYPTION_ALGORITHM,
      env.ENCRYPTION_KEY
    );
    
    // Set auth tag and AAD
    decipher.setAuthTag(Buffer.from(encryptedData.authTag, 'hex'));
    decipher.setAAD(Buffer.from(env.ENCRYPTION_KEY));
    
    // Decrypt the data
    let decryptedData = decipher.update(encryptedData.encryptedData, 'hex', 'utf8');
    decryptedData += decipher.final('utf8');
    
    return decryptedData;
  } catch (error) {
    logger.error('Decryption failed:', error);
    throw new Error('Failed to decrypt data');
  }
};

/**
 * Creates a secure hash of sensitive data (one-way, for indexing)
 * @param data - The data to hash
 * @param salt - Optional salt (if not provided, generates random)
 * @returns Hash object with hash and salt
 */
export const createSecureHash = (data: string, salt?: string): { hash: string; salt: string } => {
  try {
    const actualSalt = salt || crypto.randomBytes(32).toString('hex');
    const hash = crypto.pbkdf2Sync(data, actualSalt, 100000, 64, 'sha512').toString('hex');
    
    return { hash, salt: actualSalt };
  } catch (error) {
    logger.error('Hashing failed:', error);
    throw new Error('Failed to hash data');
  }
};

/**
 * Verifies a hash against original data
 * @param data - Original data to verify
 * @param hash - The hash to verify against
 * @param salt - The salt used for the original hash
 * @returns True if hash matches, false otherwise
 */
export const verifyHash = (data: string, hash: string, salt: string): boolean => {
  try {
    const verifyHash = crypto.pbkdf2Sync(data, salt, 100000, 64, 'sha512').toString('hex');
    return hash === verifyHash;
  } catch (error) {
    logger.error('Hash verification failed:', error);
    return false;
  }
};

/**
 * Creates a hash for phone numbers (for database indexing while preserving privacy)
 * @param phoneNumber - Phone number to hash
 * @returns Hashed phone number (deterministic for same numbers)
 */
export const hashPhoneNumber = (phoneNumber: string): string => {
  // Normalize phone number (remove spaces, dashes, etc.)
  const normalizedNumber = phoneNumber.replace(/[^\d+]/g, '');
  
  // Create deterministic hash using app secret as salt
  return crypto
    .createHmac('sha256', env.API_KEY_SECRET)
    .update(normalizedNumber)
    .digest('hex');
};

/**
 * Encrypts JSON data for database storage
 * @param data - Object to encrypt
 * @returns Encrypted string suitable for database storage
 */
export const encryptJSON = (data: any): string => {
  const jsonString = JSON.stringify(data);
  const encrypted = encrypt(jsonString);
  // Combine all parts into a single string for database storage
  return `${encrypted.iv}:${encrypted.authTag}:${encrypted.encryptedData}`;
};

/**
 * Decrypts JSON data from database storage
 * @param encryptedString - Encrypted string from database
 * @returns Original object
 */
export const decryptJSON = (encryptedString: string): any => {
  const [iv, authTag, encryptedData] = encryptedString.split(':');
  
  if (!iv || !authTag || !encryptedData) {
    throw new Error('Invalid encrypted data format');
  }
  
  const decrypted = decrypt({ iv, authTag, encryptedData });
  return JSON.parse(decrypted);
};

/**
 * Generates a secure random token for invitations, API keys, etc.
 * @param length - Length of token in bytes (default 32)
 * @returns Secure random token
 */
export const generateSecureToken = (length: number = 32): string => {
  return crypto.randomBytes(length).toString('hex');
};

/**
 * Creates a time-limited signed token for secure operations
 * @param data - Data to include in token
 * @param expiresInMinutes - Token expiration time
 * @returns Signed token
 */
export const createSignedToken = (data: any, expiresInMinutes: number = 60): string => {
  const payload = {
    data,
    exp: Date.now() + (expiresInMinutes * 60 * 1000)
  };
  
  const signature = crypto
    .createHmac('sha256', env.JWT_SECRET)
    .update(JSON.stringify(payload))
    .digest('hex');
  
  return Buffer.from(JSON.stringify({ ...payload, signature })).toString('base64');
};

/**
 * Verifies and extracts data from a signed token
 * @param token - Token to verify
 * @returns Original data if valid, null if invalid/expired
 */
export const verifySignedToken = (token: string): any | null => {
  try {
    const payload = JSON.parse(Buffer.from(token, 'base64').toString());
    
    // Check expiration
    if (Date.now() > payload.exp) {
      return null;
    }
    
    // Verify signature
    const { signature, ...dataToVerify } = payload;
    const expectedSignature = crypto
      .createHmac('sha256', env.JWT_SECRET)
      .update(JSON.stringify(dataToVerify))
      .digest('hex');
    
    if (signature !== expectedSignature) {
      return null;
    }
    
    return payload.data;
  } catch (error) {
    logger.error('Token verification failed:', error);
    return null;
  }
}; 