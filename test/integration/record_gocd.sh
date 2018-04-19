#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly MAPPING_TARGET="${SCRIPT_DIR}/gocd.tar.gz"
readonly EXAMPLE_DIR="${SCRIPT_DIR}/../../examples/go"

readonly WIREMOCK_PORT="3340"
readonly BUILDVIZ_PORT="3350"

readonly WIREMOCK_BASE_URL="http://localhost:${WIREMOCK_PORT}"
readonly BUILDVIZ_BASE_URL="http://localhost:${BUILDVIZ_PORT}"
readonly GOCD_BASE_URL="http://localhost:8153/go"
readonly SYNC_URL="${WIREMOCK_BASE_URL}/go"

readonly BUILDVIZ_TMP_DATA_DIR="/tmp/record.buildviz.$$"
readonly MAPPING_TMP_DIR="/tmp/record.wiremock.$$"

WIREMOCK_PID=
BUILDVIZ_PID=

wait_for_server() {
    local url=$1
    echo "Waiting for ${url}"
    until curl --output /dev/null --silent --head --fail "$url"; do
        printf '.'
        sleep 5
    done
    echo "up"
}

extract_base_url() {
    sed 's/^\(.*\/\/[^\/]*\).*$/\1/'
}

echo_bold() {
    local text="$1"
    echo -ne "\033[1m"
    echo "$text"
    echo -ne "\033[0m"
}

start_vagrant_image() {
    echo_bold "Starting vagrant image"
    (
        cd "$EXAMPLE_DIR"
        vagrant up
    )

    wait_for_server "$GOCD_BASE_URL"
}

stop_vagrant_image() {
    echo_bold "Stopping vagrant image"
    (
        cd "$EXAMPLE_DIR"
        vagrant halt
    )
}

start_buildviz() {
    echo_bold "Starting buildviz"

    mkdir "$BUILDVIZ_TMP_DATA_DIR"

    BUILDVIZ_DATA_DIR="$BUILDVIZ_TMP_DATA_DIR" "${SCRIPT_DIR}/../../lein" do ring server-headless "$BUILDVIZ_PORT" > /dev/null &
    BUILDVIZ_PID=$!

    wait_for_server "$BUILDVIZ_BASE_URL"
}

stop_buildviz() {
    echo_bold "Stopping buildviz"
    if [[ -n "$BUILDVIZ_PID" ]]; then
        pkill -P "$BUILDVIZ_PID" || true
    fi

    rm -rf "$BUILDVIZ_TMP_DATA_DIR"
}

start_wiremock() {
    local base_url
    base_url=$(extract_base_url <<< "$GOCD_BASE_URL")

    echo_bold "Starting wiremock"

    mkdir -p "$MAPPING_TMP_DIR"
    cd "$MAPPING_TMP_DIR"
    "$SCRIPT_DIR/start_wiremock.sh" "$WIREMOCK_PORT" &
    WIREMOCK_PID=$!
    cd - > /dev/null

    wait_for_server "${WIREMOCK_BASE_URL}/__admin"

    echo "{\"targetBaseUrl\": \"$base_url\", \"repeatsAsScenarios\": false}" \
        | curl -X POST -d@- "${WIREMOCK_BASE_URL}/__admin/recordings/start"
}

stop_wiremock() {
    echo_bold "Stopping wiremock"
    if [[ -n "$WIREMOCK_PID" ]]; then
        curl --output /dev/null -X POST "${WIREMOCK_BASE_URL}/__admin/recordings/stop" || true
        pkill -P "$WIREMOCK_PID" || true

        cd "$MAPPING_TMP_DIR"
        tar -cf "$MAPPING_TARGET" ./*
        cd - > /dev/null
    fi

    rm -rf "$MAPPING_TMP_DIR"
}

sync_builds() {
    echo_bold "Syncing..."
    "${SCRIPT_DIR}/../../lein" run -m buildviz.go.sync "$SYNC_URL" --buildviz="$BUILDVIZ_BASE_URL" --from 2000-01-01
}

clean_up() {
    stop_vagrant_image
    stop_buildviz
    stop_wiremock
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
    trap clean_up INT
    trap clean_up ERR

    start_vagrant_image
    start_buildviz
    start_wiremock

    sync_builds

    clean_up
}

main
