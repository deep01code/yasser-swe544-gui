#!/bin/bash

IDENTIFIER=$1

if [ -z "$IDENTIFIER" ]; then
  exit 1
fi

PID=$(ps aux | grep "[j]ava .* -DIDENTIFIER=$IDENTIFIER" | awk '{print $2}' | head -n 1)

if [ ! -z "$PID" ]; then
  kill -9 "$PID" > /dev/null 2>&1
fi

sleep 15

nohup java -DADDRESS=localhost \
  -DBOOTSTRAP_SERVERS=localhost:9092 \
  -DDOOR_CLUSTER_CARS_COUNTER_PATH=${IDENTIFIER%-instance*}counter \
  -DDOOR_CLUSTER_NUMBER=${IDENTIFIER%-instance*} \
  -DINSTANCE_NUMBER=${IDENTIFIER#*-instance} \
  -DIDENTIFIER=$IDENTIFIER \
  -jar swe544-0.0.1-SNAPSHOT.jar > /dev/null 2>&1 &
