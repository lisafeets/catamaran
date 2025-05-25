-- CreateEnum
CREATE TYPE "UserRole" AS ENUM ('SENIOR', 'FAMILY_MEMBER', 'ADMIN');

-- CreateEnum
CREATE TYPE "RelationshipType" AS ENUM ('CHILD', 'SPOUSE', 'PARENT', 'SIBLING', 'CAREGIVER', 'OTHER');

-- CreateTable
CREATE TABLE "users" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "password" TEXT NOT NULL,
    "firstName" TEXT,
    "lastName" TEXT,
    "phone" TEXT,
    "role" "UserRole" NOT NULL DEFAULT 'FAMILY_MEMBER',
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "emailVerified" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "lastLoginAt" TIMESTAMP(3),

    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "family_connections" (
    "id" TEXT NOT NULL,
    "seniorId" TEXT NOT NULL,
    "familyId" TEXT NOT NULL,
    "relationship" "RelationshipType" NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "connectedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "family_connections_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "call_logs" (
    "id" TEXT NOT NULL,
    "seniorId" TEXT NOT NULL,
    "phoneNumber" TEXT NOT NULL,
    "contactName" TEXT,
    "duration" INTEGER NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL,
    "callType" TEXT NOT NULL,
    "riskScore" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "isKnownContact" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "call_logs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sms_logs" (
    "id" TEXT NOT NULL,
    "seniorId" TEXT NOT NULL,
    "senderNumber" TEXT NOT NULL,
    "contactName" TEXT,
    "messageCount" INTEGER NOT NULL DEFAULT 1,
    "timestamp" TIMESTAMP(3) NOT NULL,
    "messageType" TEXT NOT NULL,
    "riskScore" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "isKnownContact" BOOLEAN NOT NULL DEFAULT false,
    "hasLink" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "sms_logs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "refresh_tokens" (
    "id" TEXT NOT NULL,
    "token" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isRevoked" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "refresh_tokens_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "users_email_key" ON "users"("email");

-- CreateIndex
CREATE UNIQUE INDEX "family_connections_seniorId_familyId_key" ON "family_connections"("seniorId", "familyId");

-- CreateIndex
CREATE INDEX "call_logs_seniorId_timestamp_idx" ON "call_logs"("seniorId", "timestamp");

-- CreateIndex
CREATE INDEX "call_logs_phoneNumber_idx" ON "call_logs"("phoneNumber");

-- CreateIndex
CREATE INDEX "sms_logs_seniorId_timestamp_idx" ON "sms_logs"("seniorId", "timestamp");

-- CreateIndex
CREATE INDEX "sms_logs_senderNumber_idx" ON "sms_logs"("senderNumber");

-- CreateIndex
CREATE UNIQUE INDEX "refresh_tokens_token_key" ON "refresh_tokens"("token");

-- AddForeignKey
ALTER TABLE "family_connections" ADD CONSTRAINT "family_connections_seniorId_fkey" FOREIGN KEY ("seniorId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "family_connections" ADD CONSTRAINT "family_connections_familyId_fkey" FOREIGN KEY ("familyId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "call_logs" ADD CONSTRAINT "call_logs_seniorId_fkey" FOREIGN KEY ("seniorId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "sms_logs" ADD CONSTRAINT "sms_logs_seniorId_fkey" FOREIGN KEY ("seniorId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "refresh_tokens" ADD CONSTRAINT "refresh_tokens_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
