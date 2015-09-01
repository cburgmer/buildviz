#!/bin/bash

curl --output /dev/null --silent --head --fail http://localhost:3000
if [ $? -eq 0 ]; then
    echo "Please stop the application running on port 3000 before continuing"
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
LOGGING_PATH="/tmp/buildviz.log"

echo "Starting buildviz... (sending output to $LOGGING_PATH)"
./lein do deps, ring server-headless > "$LOGGING_PATH" &
SERVER_PID=$!

# Wait
echo "Waiting for buildviz to come up"
wait_for_server http://localhost:3000

# Seed data
echo "Seeding data..."
"$SCRIPT_DIR"/data/seedDummyData.sh

echo "Done..."
echo
echo "Point your browser to http://localhost:3000/"
echo
echo "Later, press any key to stop the server"

read -n 1

pkill -P $SERVER_PID
