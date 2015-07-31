#!/bin/bash

curl --output /dev/null --silent --head --fail http://localhost:3000
if [ $? -eq 0 ]; then
    echo "Please stop the application running on port 3000 before continuing"
    exit 1
fi

set -e

echo "This example will download and install Go.cd in a VirtualBox and then sync its output to buildviz"
echo
echo "Press any key to continue"

read -n 1

SCRIPT_DIR=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)

function wait_for_server() {
    URL=$1
    until $(curl --output /dev/null --silent --head --fail $URL); do
        printf '.'
        sleep 5
    done
}


# Install Go.cd
echo "Installing Go.cd..."

cd "${SCRIPT_DIR}/go"
vagrant up
cd -

# Start buildviz
echo "Starting buildviz..."
./lein do deps, ring server-headless > /tmp/buildviz.log &
SERVER_PID=$!

# Wait
echo "Waiting for buildviz to come up"
wait_for_server http://localhost:3000
echo "Waiting for Go to come up"
wait_for_server http://localhost:8153/go

# Sync buildviz with the Go builds
echo "Syncing job history..."
./lein exec scripts/gosync.clj http://localhost:8153/go --from 2014-06-01

echo "Done..."
echo
echo "Point your browser to http://localhost:3000/"
echo
echo "Later, press any key to stop the server and bring down the vagrant box"

read -n 1

echo "Taking down Vagrant instance and buildviz..."

cd "${SCRIPT_DIR}/go"
vagrant halt
cd -

pkill -P $SERVER_PID
