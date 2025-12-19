#!/bin/bash

set -e

# Check if .env file exists, if not create it with default values
if [ ! -f .env ]; then
  echo "Creating .env file with default values..."
  cat > .env << EOF
POSTGRES_DB=mydb
POSTGRES_USER=app
POSTGRES_PASSWORD=app
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin
EOF
  echo ".env file created!"
fi

# Load environment variables from .env file
export $(cat .env | grep -v '^#' | xargs)

echo "Starting Keycloak and Postgres..."
docker compose up -d keycloak db

echo "Waiting for PostgreSQL to become ready..."
# Wait for PostgreSQL container to be running
MAX_ATTEMPTS=30
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  # Check if container is running (not restarting)
  CONTAINER_STATUS=$(docker ps -a --filter "name=postgres_app" --format "{{.Status}}" 2>/dev/null || echo "")
  if echo "$CONTAINER_STATUS" | grep -q "Up"; then
    # Container is running, now check if PostgreSQL is ready
    if docker exec postgres_app pg_isready -U ${POSTGRES_USER:-app} > /dev/null 2>&1; then
      echo "PostgreSQL is ready!"
      break
    fi
  elif echo "$CONTAINER_STATUS" | grep -q "Restarting"; then
    echo "PostgreSQL container is restarting... waiting..."
  fi
  ATTEMPT=$((ATTEMPT + 1))
  echo "Waiting for PostgreSQL... ($ATTEMPT/$MAX_ATTEMPTS)"
  sleep 2
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
  echo "ERROR: PostgreSQL did not become ready in time!"
  echo "Checking PostgreSQL logs..."
  docker logs postgres_app --tail 20
  exit 1
fi

echo "Waiting a bit more for Keycloak..."
sleep 5

echo "Starting Spring Boot backend..."
(
  cd backend
  mvn -q clean spring-boot:run
) &

BACKEND_PID=$!

echo "Starting Angular frontend..."
(
  cd frontend
  ng serve --host 0.0.0.0 --port 4200
) &

FRONTEND_PID=$!

echo ""
echo "Development environment running."
echo "Backend PID:   $BACKEND_PID"
echo "Frontend PID:  $FRONTEND_PID"
echo ""
echo "Keycloak: http://localhost:9090"
echo "Backend:  http://localhost:8081"
echo "Frontend: http://localhost:4200"
echo ""
echo "Press CTRL+C to stop all."
echo ""

cleanup() {
    echo ""
    echo "Stopping dev environment..."

    echo "Stopping Spring Boot backend..."
    kill $BACKEND_PID 2>/dev/null || true

    echo "Stopping Angular frontend..."
    kill $FRONTEND_PID 2>/dev/null || true

    echo "Stopping Docker services..."
    docker compose down

    echo "Done."
    exit 0
}

# Handle Ctrl+C — stop everything gracefully
trap cleanup INT

wait
