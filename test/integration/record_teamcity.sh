#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly MAPPING_TARGET="${SCRIPT_DIR}/teamcity.tar.gz"
readonly EXAMPLE_DIR="${SCRIPT_DIR}/../../examples/teamcity"
readonly DATA_DIR="${SCRIPT_DIR}/../../examples/data"

readonly WIREMOCK_PORT="3342"
readonly BUILDVIZ_PORT="3352"

readonly WIREMOCK_BASE_URL="http://localhost:${WIREMOCK_PORT}"
readonly BUILDVIZ_BASE_URL="http://localhost:${BUILDVIZ_PORT}"
readonly TEAMCITY_BASE_URL="http://localhost:8111"
readonly SYNC_URL="http://admin:admin@localhost:${WIREMOCK_PORT}"

readonly MAPPING_TMP_DIR="/tmp/record.wiremock.$$"


extract_base_url() {
    sed 's/^\(.*\/\/[^\/]*\).*$/\1/'
}

start_container() {
    "$EXAMPLE_DIR/run.sh" start
}

stop_container() {
    "$EXAMPLE_DIR/run.sh" stop
}

start_buildviz() {
    PORT="$BUILDVIZ_PORT" "${DATA_DIR}/run_buildviz.sh" start
}

stop_buildviz() {
    "${DATA_DIR}/run_buildviz.sh" stop
}

start_wiremock() {
    "${SCRIPT_DIR}/run_wiremock.sh" install
    mkdir -p "$MAPPING_TMP_DIR"
    ROOT_DIR="$MAPPING_TMP_DIR" PORT="$WIREMOCK_PORT" "${SCRIPT_DIR}/run_wiremock.sh" start
}

stop_wiremock() {
    "${SCRIPT_DIR}/run_wiremock.sh" stop

    rm -rf "$MAPPING_TMP_DIR"
}

sync_builds() {
    "${SCRIPT_DIR}/../../lein" run -m buildviz.teamcity.sync "$SYNC_URL" --buildviz="$BUILDVIZ_BASE_URL" --from 2000-01-01 -p SimpleSetup
}

start_recording() {
    echo "{\"targetBaseUrl\": \"$TEAMCITY_BASE_URL\", \"repeatsAsScenarios\": false}" \
        | curl --fail --silent --output /dev/null -X POST -d@- "${WIREMOCK_BASE_URL}/__admin/recordings/start"
}

finish_recording() {
    curl --fail --silent --output /dev/null -X POST "${WIREMOCK_BASE_URL}/__admin/recordings/stop"
    cd "$MAPPING_TMP_DIR"
    tar -czf "$MAPPING_TARGET" ./*
    cd - > /dev/null

    echo "Recorded in ${MAPPING_TARGET}"
}

clean_up() {
    stop_container
    stop_buildviz
    stop_wiremock
    echo
}

ensure_empty_mappings() {
    if [[ -e "$MAPPING_TARGET" ]]; then
        echo "Please remove ${MAPPING_TARGET} first"
        exit 1
    fi
}

main() {
    ensure_empty_mappings

    # Handle Ctrl+C and errors
    trap clean_up EXIT

    start_container
    start_buildviz
    start_wiremock
    start_recording

    sync_builds
    finish_recording
}

main
