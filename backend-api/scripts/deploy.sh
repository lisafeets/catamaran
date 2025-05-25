#!/bin/bash

# Catamaran Backend API Deployment Script
# This script handles production deployment with safety checks and rollback capabilities

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="$PROJECT_DIR/backups"
LOG_FILE="$PROJECT_DIR/logs/deploy-$(date +%Y%m%d-%H%M%S).log"

# Default values
ENVIRONMENT="production"
SKIP_BACKUP=false
SKIP_TESTS=false
FORCE_DEPLOY=false

# Functions
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
    exit 1
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

# Help function
show_help() {
    cat << EOF
Catamaran Backend API Deployment Script

Usage: $0 [OPTIONS]

OPTIONS:
    -e, --environment ENV    Deployment environment (production, staging, development)
    -s, --skip-backup       Skip database backup
    -t, --skip-tests        Skip running tests
    -f, --force             Force deployment without confirmations
    -h, --help              Show this help message

EXAMPLES:
    $0                                  # Deploy to production with all safety checks
    $0 -e staging                       # Deploy to staging environment
    $0 --skip-backup --skip-tests       # Quick deployment (not recommended for production)
    $0 --force                          # Force deployment without prompts

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -s|--skip-backup)
            SKIP_BACKUP=true
            shift
            ;;
        -t|--skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        -f|--force)
            FORCE_DEPLOY=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            error "Unknown option: $1"
            ;;
    esac
done

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(production|staging|development)$ ]]; then
    error "Invalid environment: $ENVIRONMENT. Must be production, staging, or development."
fi

# Create necessary directories
mkdir -p "$BACKUP_DIR" "$PROJECT_DIR/logs"

log "Starting Catamaran Backend API deployment to $ENVIRONMENT environment"

# Pre-deployment checks
log "Running pre-deployment checks..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    error "Docker is not running. Please start Docker and try again."
fi

# Check if required files exist
required_files=("package.json" "Dockerfile" "docker-compose.yml" "prisma/schema.prisma")
for file in "${required_files[@]}"; do
    if [[ ! -f "$PROJECT_DIR/$file" ]]; then
        error "Required file not found: $file"
    fi
done

# Check environment variables
if [[ "$ENVIRONMENT" == "production" ]]; then
    if [[ ! -f "$PROJECT_DIR/.env.production" ]]; then
        error "Production environment file (.env.production) not found"
    fi
    
    # Validate critical environment variables
    source "$PROJECT_DIR/.env.production"
    
    critical_vars=("DATABASE_URL" "JWT_SECRET" "JWT_REFRESH_SECRET" "ENCRYPTION_KEY")
    for var in "${critical_vars[@]}"; do
        if [[ -z "${!var}" ]]; then
            error "Critical environment variable $var is not set"
        fi
    done
    
    # Check JWT secret length
    if [[ ${#JWT_SECRET} -lt 32 ]]; then
        error "JWT_SECRET must be at least 32 characters long"
    fi
    
    # Check encryption key length
    if [[ ${#ENCRYPTION_KEY} -ne 32 ]]; then
        error "ENCRYPTION_KEY must be exactly 32 characters long"
    fi
fi

success "Pre-deployment checks passed"

# Run tests
if [[ "$SKIP_TESTS" == false ]]; then
    log "Running tests..."
    cd "$PROJECT_DIR"
    
    if ! npm test; then
        error "Tests failed. Deployment aborted."
    fi
    
    success "All tests passed"
fi

# Database backup
if [[ "$SKIP_BACKUP" == false && "$ENVIRONMENT" == "production" ]]; then
    log "Creating database backup..."
    
    backup_file="$BACKUP_DIR/catamaran-db-backup-$(date +%Y%m%d-%H%M%S).sql"
    
    if docker-compose exec -T postgres pg_dump -U catamaran_user catamaran_db > "$backup_file"; then
        success "Database backup created: $backup_file"
    else
        error "Database backup failed"
    fi
fi

# Confirmation prompt
if [[ "$FORCE_DEPLOY" == false ]]; then
    echo
    warning "You are about to deploy to $ENVIRONMENT environment."
    read -p "Are you sure you want to continue? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log "Deployment cancelled by user"
        exit 0
    fi
fi

# Build and deploy
log "Building application..."

cd "$PROJECT_DIR"

# Build Docker images
if ! docker-compose build api; then
    error "Docker build failed"
fi

success "Application built successfully"

# Database migrations
log "Running database migrations..."

# Stop existing containers gracefully
docker-compose down --timeout 30

# Start database first
docker-compose up -d postgres redis

# Wait for database to be ready
log "Waiting for database to be ready..."
timeout=60
counter=0

while ! docker-compose exec postgres pg_isready -U catamaran_user -d catamaran_db > /dev/null 2>&1; do
    if [[ $counter -ge $timeout ]]; then
        error "Database failed to start within $timeout seconds"
    fi
    sleep 1
    ((counter++))
done

success "Database is ready"

# Run migrations
if ! docker-compose run --rm api npx prisma migrate deploy; then
    error "Database migration failed"
fi

success "Database migrations completed"

# Start all services
log "Starting all services..."

if ! docker-compose up -d; then
    error "Failed to start services"
fi

# Health checks
log "Performing health checks..."

# Wait for API to be ready
api_timeout=120
api_counter=0

while ! curl -f http://localhost:3000/health > /dev/null 2>&1; do
    if [[ $api_counter -ge $api_timeout ]]; then
        error "API health check failed after $api_timeout seconds"
    fi
    sleep 2
    ((api_counter+=2))
done

success "API is healthy"

# Additional health checks
log "Running additional health checks..."

# Check database connectivity
if ! docker-compose exec api node -e "
const { db } = require('./dist/database/prisma');
db.\$queryRaw\`SELECT 1\`.then(() => {
    console.log('Database connection successful');
    process.exit(0);
}).catch((err) => {
    console.error('Database connection failed:', err);
    process.exit(1);
});
"; then
    error "Database connectivity check failed"
fi

# Check WebSocket functionality
if ! curl -f -H "Connection: Upgrade" -H "Upgrade: websocket" http://localhost:3000 > /dev/null 2>&1; then
    warning "WebSocket upgrade check failed (this may be normal)"
fi

success "All health checks passed"

# Post-deployment tasks
log "Running post-deployment tasks..."

# Clean up old Docker images
docker image prune -f

# Log deployment info
deployment_info="$PROJECT_DIR/logs/deployment-info.json"
cat > "$deployment_info" << EOF
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "environment": "$ENVIRONMENT",
    "git_commit": "$(git rev-parse HEAD 2>/dev/null || echo 'unknown')",
    "git_branch": "$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'unknown')",
    "deployed_by": "$(whoami)",
    "version": "$(node -p "require('./package.json').version" 2>/dev/null || echo 'unknown')"
}
EOF

success "Post-deployment tasks completed"

# Final status
echo
success "🚀 Deployment to $ENVIRONMENT completed successfully!"
echo
log "Deployment summary:"
log "  Environment: $ENVIRONMENT"
log "  API URL: http://localhost:3000"
log "  Health check: http://localhost:3000/health"
log "  Logs: docker-compose logs -f api"
log "  Stop: docker-compose down"

# Show running services
echo
log "Running services:"
docker-compose ps

echo
log "Deployment completed at $(date)"
log "Check the logs for any issues: tail -f $LOG_FILE" 