#!/bin/bash
set -e

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

cd "$SCRIPT_DIR"
if [ ! -d "node_modules" ]; then
    npm install csscritic
fi

cd "$SCRIPT_DIR/.."
python -m SimpleHTTPServer
