#!/bin/bash

# Catamaran Backend Development Startup Script

echo "🛡️  Starting Catamaran Backend Development Server"
echo "=================================================="

# Check if .env exists
if [ ! -f .env ]; then
    echo "❌ .env file not found!"
    echo "Please copy env.example to .env and configure your settings:"
    echo "cp env.example .env"
    exit 1
fi

# Check if node_modules exists
if [ ! -d node_modules ]; then
    echo "📦 Installing dependencies..."
    npm install
fi

# Check if Prisma client is generated
if [ ! -d node_modules/.prisma ]; then
    echo "🔧 Generating Prisma client..."
    npm run prisma:generate
fi

# Create logs directory if it doesn't exist
mkdir -p logs

echo ""
echo "🚀 Starting development server..."
echo "   - Server: http://localhost:3001"
echo "   - Health: http://localhost:3001/health"
echo "   - Logs: ./logs/"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Start the development server
npm run dev 