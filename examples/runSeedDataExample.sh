#!/bin/bash

PORT=3333

curl --output /dev/null --silent --head --fail http://localhost:$PORT
if [ $? -eq 0 ]; then
    echo "Please stop the application running on port $PORT before continuing"
    exit 1
fi

set -e

SCRIPT_DIR=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)

function wait_for_server() {
    URL=$1
    until $(curl --output /dev/null --silent --head --fail $URL); do
        printf '.'
        sleep 5
    done
}


# Start buildviz
TMP_DIR="/tmp/buildviz.$$"
LOGGING_PATH="${TMP_DIR}/buildviz.log"

mkdir -p "$TMP_DIR"

echo "Starting buildviz... (sending stdout to $LOGGING_PATH)"
BUILDVIZ_DATA_DIR=$TMP_DIR ./lein do deps, ring server-headless $PORT > "$LOGGING_PATH" &
SERVER_PID=$!

# Wait
echo "Waiting for buildviz to come up"
wait_for_server http://localhost:$PORT

# Seed data
echo "Seeding data..."
PORT="${PORT}" "$SCRIPT_DIR"/data/seedDummyData.sh

echo "Done..."
echo
echo "Point your browser to http://localhost:$PORT/"
echo
echo "Later, press any key to stop the server"

read -n 1

pkill -P $SERVER_PID
