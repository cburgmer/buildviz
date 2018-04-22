#!/bin/bash
set -e

SCRIPT_DIR=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)

lint() {
    shellcheck examples/**/*.sh examples/*.sh test/integration/*.sh
}

unit_test() {
    "${SCRIPT_DIR}/lein" test
}

test_integration() {
    echo
    echo "Running integration test against recorded endpoints."
    echo "If this fails you might have changed how the endpoints are requested, and might want to record from scratch."
    echo "Testing buildviz.go.sync"
    "${SCRIPT_DIR}/test/integration/test_gocd.sh"
    echo "Testing buildviz.jenkins.sync"
    "${SCRIPT_DIR}/test/integration/test_jenkins.sh"
}

test_example() {
    echo
    echo "Running simple example to make sure it doesn't break"
    yes | "${SCRIPT_DIR}/examples/runSeedDataExample.sh"
}

main() {
    lint
    unit_test
    test_integration
    test_example
}

main
