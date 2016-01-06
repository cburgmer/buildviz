#!/bin/bash
set -e

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

cd "$SCRIPT_DIR"
if [ ! -d "node_modules" ]; then
    npm install csscritic
fi

echo "Please go to http://localhost:8000/test/RegressionRunner.html"

cd "$SCRIPT_DIR/.."
python -m SimpleHTTPServer
