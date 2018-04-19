#!/bin/bash
set -e

SCRIPT_DIR=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)

unit_test() {
    "${SCRIPT_DIR}/lein" test
}

test_integration() {
    echo "Running integration test against recorded endpoints."
    echo "If this fails you might have changed how the endpoints are requested, and might want to record from scratch."
    "${SCRIPT_DIR}/test/integration/test_gocd.sh"
}

test_example() {
    echo "Running simple example to make sure it doesn't break"
    yes | "${SCRIPT_DIR}/examples/runSeedDataExample.sh"
}

main() {
    unit_test
    test_integration
    test_example
}

main
