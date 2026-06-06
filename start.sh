#!/usr/bin/env bash
set -e

# Navigate to script directory
cd "$(dirname "$0")"

echo "=========================================="
echo "Initializing Event Ledger Container Stack"
echo "=========================================="

# Parse command line options
CLEAN_START=false
for arg in "$@"; do
  if [ "$arg" == "--clean" ] || [ "$arg" == "-c" ]; then
    CLEAN_START=true
  fi
done

if [ "$CLEAN_START" = true ]; then
  echo "Clean start requested. Stopping existing containers..."
  ./stop.sh
fi

# List of required ports on the host
REQUIRED_PORTS=(8080 8081 3001 9090 8025)

echo "Checking if required ports are free..."
CONFLICTS=()
for port in "${REQUIRED_PORTS[@]}"; do
  # Check if port is in use on TCP
  if lsof -Pi :"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
    PID=$(lsof -Pi :"$port" -sTCP:LISTEN -t | head -n 1)
    PROCESS_NAME=$(ps -p "$PID" -o comm= 2>/dev/null || echo "unknown")
    
    # If the port is held by docker/docker-proxy, check if it belongs to our own compose stack.
    # If we are doing a clean start, it shouldn't be in use at all.
    # Otherwise, if our stack is already up, we can ignore our own docker-proxy.
    IS_OUR_DOCKER=false
    if [[ "$PROCESS_NAME" == *"docker"* ]] && [ "$CLEAN_START" = false ]; then
      # Check if this port is mapped by a running container in our stack
      if docker compose ps --services --filter "status=running" >/dev/null 2>&1; then
        IS_OUR_DOCKER=true
      fi
    fi
    
    if [ "$IS_OUR_DOCKER" = false ]; then
      echo "WARNING: Port $port is already in use by process $PROCESS_NAME (PID: $PID)"
      CONFLICTS+=("$port")
    fi
  fi
done

if [ ${#CONFLICTS[@]} -ne 0 ]; then
  echo "Error: Cannot start containers due to external port conflicts on port(s): ${CONFLICTS[*]}"
  echo "Please stop the conflicting processes or run with '--clean' if they are old containers."
  exit 1
fi

echo "Ports are free. Starting optimized Gradle build..."
# Build JARs in parallel to speed up compilation
./gradlew bootJar --parallel --build-cache

echo "Starting docker-compose services..."
docker compose up -d --build

echo "Waiting for services to become healthy..."
MAX_ATTEMPTS=45
ATTEMPT=0
while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  # Get names of all services that have a healthcheck configured
  # and verify that none are in starting/unhealthy states.
  UNHEALTHY=$(docker compose ps --format json | grep -E '"State":"(starting|unhealthy)"' || true)
  
  if [ -z "$UNHEALTHY" ]; then
    # Double check if any service is not running
    NOT_RUNNING=$(docker compose ps --format json | grep -v '"State":"running"' || true)
    if [ -z "$NOT_RUNNING" ]; then
      echo "All services are healthy and running!"
      break
    fi
  fi
  
  ATTEMPT=$((ATTEMPT+1))
  echo "Waiting for services to be healthy (attempt $ATTEMPT/$MAX_ATTEMPTS)..."
  sleep 2
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
  echo "Error: Some services did not become healthy in time. Current status:"
  docker compose ps
  echo "Logs for unhealthy services:"
  docker compose logs --tail=50
  exit 1
fi

echo "=========================================="
echo "Startup complete! Services running at:"
echo " - Gateway API: http://localhost:8080"
echo "   - Swagger:   http://localhost:8080/swagger-ui/index.html"
echo " - Account API: http://localhost:8081"
echo "   - Swagger:   http://localhost:8081/swagger-ui/index.html"
echo " - Grafana:     http://localhost:3001"
echo " - Prometheus:  http://localhost:9090"
echo " - Mailpit UI:  http://localhost:8025"
echo "=========================================="
