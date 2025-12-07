#!/bin/bash

set -e

echo "Starting Keycloak and Postgres..."
docker compose up -d keycloak db

echo "Waiting for Keycloak and Postgres to become ready..."
sleep 10

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
