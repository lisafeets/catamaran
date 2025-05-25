#!/bin/bash

# Catamaran Web Dashboard Deployment Script
# This script builds and deploys the React web dashboard

set -e  # Exit on any error

echo "🚀 Starting Catamaran Web Dashboard Deployment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    print_error "Node.js is not installed. Please install Node.js 16+ and try again."
    exit 1
fi

# Check Node.js version
NODE_VERSION=$(node --version | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 16 ]; then
    print_error "Node.js version 16+ is required. Current version: $(node --version)"
    exit 1
fi

print_status "Node.js version: $(node --version) ✓"

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    print_error "npm is not installed. Please install npm and try again."
    exit 1
fi

print_status "npm version: $(npm --version) ✓"

# Install dependencies if node_modules doesn't exist
if [ ! -d "node_modules" ]; then
    print_status "Installing dependencies..."
    npm install
    print_success "Dependencies installed"
else
    print_status "Dependencies already installed"
fi

# Check for environment variables
if [ ! -f ".env.local" ] && [ -z "$REACT_APP_API_URL" ]; then
    print_warning "No .env.local file found and REACT_APP_API_URL not set"
    print_warning "Creating default .env.local file..."
    cat > .env.local << EOF
# Development Configuration
REACT_APP_API_URL=http://localhost:3000/api
REACT_APP_WS_URL=ws://localhost:3000
REACT_APP_ENABLE_DEVTOOLS=true
EOF
    print_success "Created .env.local with default values"
fi

# Build the application
print_status "Building production bundle..."
npm run build

if [ $? -eq 0 ]; then
    print_success "Build completed successfully"
else
    print_error "Build failed"
    exit 1
fi

# Check build size
BUILD_SIZE=$(du -sh build | cut -f1)
print_status "Build size: $BUILD_SIZE"

# Deployment options
echo ""
echo "🎉 Build completed successfully!"
echo ""
echo "📦 Deployment Options:"
echo ""
echo "1. 🌐 Serve locally:"
echo "   npm install -g serve"
echo "   serve -s build -l 3000"
echo ""
echo "2. 📤 Deploy to Netlify:"
echo "   - Drag and drop the 'build' folder to netlify.com/drop"
echo "   - Or use Netlify CLI: netlify deploy --prod --dir=build"
echo ""
echo "3. 🚀 Deploy to Vercel:"
echo "   - Install Vercel CLI: npm i -g vercel"
echo "   - Run: vercel --prod"
echo ""
echo "4. ☁️ Deploy to AWS S3:"
echo "   - aws s3 sync build/ s3://your-bucket-name --delete"
echo "   - Configure CloudFront for HTTPS"
echo ""
echo "5. 📱 Test PWA features:"
echo "   - Serve over HTTPS for full PWA functionality"
echo "   - Test installation on mobile devices"
echo ""

print_success "Deployment preparation complete!" 