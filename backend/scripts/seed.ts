import { PrismaClient } from '@prisma/client';
import bcrypt from 'bcrypt';

const prisma = new PrismaClient();

async function main() {
  console.log('🌱 Starting database seed...');

  // Clear existing data
  await prisma.refreshToken.deleteMany();
  await prisma.smsLog.deleteMany();
  await prisma.callLog.deleteMany();
  await prisma.familyConnection.deleteMany();
  await prisma.user.deleteMany();

  console.log('✅ Cleared existing data');

  // Create users
  const hashedPassword = await bcrypt.hash('password123', 12);

  // Senior user
  const seniorUser = await prisma.user.create({
    data: {
      email: 'grandma@example.com',
      password: hashedPassword,
      firstName: 'Margaret',
      lastName: 'Johnson',
      phone: '+1-555-0101',
      role: 'SENIOR',
      isActive: true,
    },
  });

  // Family member 1
  const familyMember1 = await prisma.user.create({
    data: {
      email: 'sarah@example.com',
      password: hashedPassword,
      firstName: 'Sarah',
      lastName: 'Johnson',
      phone: '+1-555-0102',
      role: 'FAMILY_MEMBER',
      isActive: true,
    },
  });

  // Family member 2
  const familyMember2 = await prisma.user.create({
    data: {
      email: 'mike@example.com',
      password: hashedPassword,
      firstName: 'Michael',
      lastName: 'Johnson',
      phone: '+1-555-0103',
      role: 'FAMILY_MEMBER',
      isActive: true,
    },
  });

  console.log('✅ Created users');

  // Create family connections
  await prisma.familyConnection.create({
    data: {
      seniorId: seniorUser.id,
      familyId: familyMember1.id,
      relationship: 'CHILD',
      isActive: true,
    },
  });

  await prisma.familyConnection.create({
    data: {
      seniorId: seniorUser.id,
      familyId: familyMember2.id,
      relationship: 'CHILD',
      isActive: true,
    },
  });

  console.log('✅ Created family connections');

  // Create call logs
  const now = new Date();
  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  const twoDaysAgo = new Date(now.getTime() - 2 * 24 * 60 * 60 * 1000);

  await prisma.callLog.createMany({
    data: [
      {
        seniorId: seniorUser.id,
        phoneNumber: '+1-555-0102',
        contactName: 'Sarah',
        callType: 'outgoing',
        duration: 320,
        timestamp: now,
        isKnownContact: true,
      },
      {
        seniorId: seniorUser.id,
        phoneNumber: '+1-555-0199',
        contactName: 'Unknown Caller',
        callType: 'incoming',
        duration: 0,
        timestamp: yesterday,
        riskScore: 0.8,
      },
      {
        seniorId: seniorUser.id,
        phoneNumber: '+1-555-0103',
        contactName: 'Michael',
        callType: 'incoming',
        duration: 180,
        timestamp: twoDaysAgo,
        isKnownContact: true,
      },
      {
        seniorId: seniorUser.id,
        phoneNumber: '+1-800-SCAM',
        contactName: 'Suspicious Number',
        callType: 'incoming',
        duration: 45,
        timestamp: yesterday,
        riskScore: 0.9,
      },
    ],
  });

  console.log('✅ Created call logs');

  // Create SMS logs
  await prisma.smsLog.createMany({
    data: [
      {
        seniorId: seniorUser.id,
        senderNumber: '+1-555-0102',
        contactName: 'Sarah',
        messageType: 'received',
        timestamp: now,
        isKnownContact: true,
      },
      {
        seniorId: seniorUser.id,
        senderNumber: '+1-555-0102',
        contactName: 'Sarah',
        messageType: 'sent',
        timestamp: new Date(now.getTime() + 5 * 60 * 1000),
        isKnownContact: true,
      },
      {
        seniorId: seniorUser.id,
        senderNumber: '+1-555-SPAM',
        contactName: 'Unknown',
        messageType: 'received',
        timestamp: yesterday,
        hasLink: true,
        riskScore: 0.9,
      },
      {
        seniorId: seniorUser.id,
        senderNumber: '+1-555-0103',
        contactName: 'Michael',
        messageType: 'received',
        timestamp: twoDaysAgo,
        isKnownContact: true,
      },
    ],
  });

  console.log('✅ Created SMS logs');

  console.log('🎉 Database seeded successfully!');
  console.log('\n📋 Test Data Summary:');
  console.log(`👵 Senior User: grandma@example.com (password: password123)`);
  console.log(`👩 Family Member 1: sarah@example.com (password: password123)`);
  console.log(`👨 Family Member 2: mike@example.com (password: password123)`);
  console.log(`📞 ${await prisma.callLog.count()} call logs created`);
  console.log(`💬 ${await prisma.smsLog.count()} SMS logs created`);
  console.log(`👨‍👩‍👧‍👦 ${await prisma.familyConnection.count()} family connections created`);
}

main()
  .catch((e) => {
    console.error('❌ Error seeding database:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  }); 