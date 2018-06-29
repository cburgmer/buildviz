#!/bin/bash
set -eo pipefail

is_successful() {
    local outcome="$1"
    test "$outcome" -ne 0
}

simulate_runtime() {
    local runtime=$(( RANDOM % 10 ))
    echo "Sleeping ${runtime}s"
    sleep "$runtime"
}

main() {
    local fail_every_run_no="$1"
    local outcome
    outcome=$((GO_PIPELINE_COUNTER % fail_every_run_no))

    simulate_runtime

    is_successful "$outcome"
}

main "$@"
