#!/bin/bash

# Usage: ./restartInstance.sh <identifier>
IDENTIFIER=$1

if [ -z "$IDENTIFIER" ]; then
  echo "Error: Instance identifier is required."
  exit 1
fi

# Kill the process associated with the identifier
echo "Finding and killing the process for $IDENTIFIER..."
PID=$(ps aux | grep "$IDENTIFIER" | grep -v grep | awk '{print $2}' | head -n 1)

if [ -z "$PID" ]; then
  echo "No running process found for $IDENTIFIER. Skipping kill step."
else
  kill -9 "$PID"
  if [ $? -eq 0 ]; then
    echo "Process $PID for $IDENTIFIER has been successfully killed."
  else
    echo "Failed to kill process $PID for $IDENTIFIER."
  fi
fi

# Restart the instance
echo "Restarting $IDENTIFIER..."
nohup java -DADDRESS=localhost \
  -DBOOTSTRAP_SERVERS=localhost:9092 \
  -DDOOR_CLUSTER_CARS_COUNTER_PATH=${IDENTIFIER%-instance*}counter \
  -DDOOR_CLUSTER_NUMBER=${IDENTIFIER%-instance*} \
  -DINSTANCE_NUMBER=${IDENTIFIER#*-instance} \
  -DIDENTIFIER=$IDENTIFIER \
  -jar build/libs/swe544-0.0.1-SNAPSHOT.jar &

echo "$IDENTIFIER has been restarted."
