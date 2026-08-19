#!/usr/bin/env bash

# Build the application with Quarkus AOT (Ahead-of-Time)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building runner jar with Quarkus AOT..."
./mvnw clean verify -DskipITs=false \
  -Dquarkus.package.jar.aot.enabled=true \
  -Dquarkus.package.jar.aot.type=aot \
  -Dquarkus.package.jar.aot.phase=build \
  -Dquarkus.package.jar.aot.additional-recording-args=-XX:+UseCompactObjectHeaders \
  "$@"

AOT_CACHE="$SCRIPT_DIR/target/quarkus-app/app.aot"

# The one-step training run leaves a large intermediate config file behind.
rm -f "$SCRIPT_DIR/target/quarkus-app/app.aotconf"

if [[ -f "$AOT_CACHE" ]]; then
  echo ""
  echo "AOT build successful. Cache: $AOT_CACHE"
  echo "Run with: ./ljs <command> [args...]"
else
  echo ""
  echo "Build completed but AOT cache not found at $AOT_CACHE"
  echo "The application will still work, but without AOT startup optimization."
fi
