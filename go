#!/bin/bash
# Full test run
set -e

SCRIPT_DIR=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)

function unit_test {
    "$SCRIPT_DIR/lein" test
}

function end2end_test {
    yes | "$SCRIPT_DIR/examples/runSeedDataExample.sh"
}

function main {
    unit_test
    end2end_test
}

main
