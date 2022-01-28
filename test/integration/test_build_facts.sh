#!/bin/bash
set -eo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly SCRIPT_DIR

readonly MAPPING_SOURCE="${SCRIPT_DIR}/jenkins.tar.gz"
readonly DATA_DIR="${SCRIPT_DIR}/../../examples/data"

readonly WIREMOCK_PORT="3341"
readonly BUILDVIZ_PORT="3351"

readonly WIREMOCK_BASE_URL="http://localhost:${WIREMOCK_PORT}"
readonly BUILDVIZ_BASE_URL="http://localhost:${BUILDVIZ_PORT}"
readonly SYNC_URL="$WIREMOCK_BASE_URL"

readonly MAPPING_TMP_DIR="/tmp/record.wiremock.$$"
readonly TMP_OUTPUT_DIR="/tmp/build-facts.$$"

start_buildviz() {
    PORT="$BUILDVIZ_PORT" "${DATA_DIR}/run_buildviz.sh" start
}

stop_buildviz() {
    "${DATA_DIR}/run_buildviz.sh" stop
}

start_wiremock() {
    mkdir "$MAPPING_TMP_DIR"
    tar -xzf "$MAPPING_SOURCE" -C "$MAPPING_TMP_DIR"

    "${SCRIPT_DIR}/run_wiremock.sh" install
    ROOT_DIR="$MAPPING_TMP_DIR" PORT="$WIREMOCK_PORT" "${SCRIPT_DIR}/run_wiremock.sh" start
}

stop_wiremock() {
    "${SCRIPT_DIR}/run_wiremock.sh" stop

    rm -rf "$MAPPING_TMP_DIR"
}

die() {
    local message="$1"
    local output="$2"
    >&2 echo
    >&2 echo "Failed: ${message}"
    >&2 echo
    >&2 cat "$output"
    >&2 echo
    exit 1
}

sync_builds() {
    local build_facts_version="0.5.4"
    local jar="build-facts-${build_facts_version}-standalone.jar"
    local stdout="${TMP_OUTPUT_DIR}/stdout"
    local stderr="${TMP_OUTPUT_DIR}/stderr"
    local curl_output="${TMP_OUTPUT_DIR}/curl_output"

    mkdir -p "$TMP_OUTPUT_DIR"

    if [[ ! -f "$jar" ]]; then
        echo "Downloading ${jar}"
        curl -L "https://github.com/cburgmer/build-facts/releases/download/${build_facts_version}/build-facts-${build_facts_version}-standalone.jar" -o "$jar"
    fi
    java -jar "$jar" jenkins "$SYNC_URL" --from 2000-01-01 > "$stdout" 2> "$stderr"

    grep "Synced 10 builds" < "$stderr" || die "Did not receive success message in:" "$stderr"

    curl -v -H "Content-type: text/plain" -d"@${stdout}" "${BUILDVIZ_BASE_URL}/builds" > "$curl_output" 2>&1
    grep "204 No Content" < "$curl_output" || die "curl failed syching builds:" "$curl_output"
}

clean_up() {
    stop_buildviz
    stop_wiremock

    rm -rf "$TMP_OUTPUT_DIR"
}

main() {
    # Handle Ctrl+C and errors
    trap clean_up EXIT

    start_buildviz
    start_wiremock

    sync_builds
}

main
