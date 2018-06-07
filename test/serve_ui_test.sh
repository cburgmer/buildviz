#!/bin/bash
set -e

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

cd "$SCRIPT_DIR"
if [ ! -d "node_modules" ]; then
    npm install csscritic http-server
fi

echo "Please go to http://localhost:8000/test/RegressionRunner.html"

cd "$SCRIPT_DIR/.."
"$SCRIPT_DIR"/node_modules/.bin/http-server -p 8000 -a 127.0.0.1
