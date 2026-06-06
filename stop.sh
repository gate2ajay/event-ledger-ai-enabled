#!/usr/bin/env bash

# Stop and clean up containers, volumes, networks, and orphans
echo "Stopping all containers and cleaning up resources..."
docker compose down -v --remove-orphans
echo "Clean up completed."
