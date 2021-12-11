#!/bin/bash
set -eo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly SCRIPT_DIR

readonly VERSION="2.16.0"
readonly ARTIFACT_NAME="wiremock-standalone"
readonly FILENAME="${ARTIFACT_NAME}-${VERSION}.jar"
readonly FILEPATH="${SCRIPT_DIR}/${FILENAME}"

readonly TMP_LOG="/tmp/run.wiremock.log"
readonly PID_FILE="${SCRIPT_DIR}/wiremock.pid"

wait_for_server() {
    local url=$1
    echo -n " waiting for ${url}"
    until curl --output /dev/null --silent --head --fail "$url"; do
        printf '.'
        sleep 5
    done
}

announce() {
    local text="$1"
    echo -ne "\033[1;30m"
    echo -n "$text"
    echo -ne "\033[0m"
}

hint_at_logs() {
    # shellcheck disable=SC2181
    if [[ "$?" -ne 0 ]]; then
        echo
        echo "Logs are in ${TMP_LOG}"
    fi
}

goal_install() {
    local url="https://repo1.maven.org/maven2/com/github/tomakehurst/${ARTIFACT_NAME}/${VERSION}/${FILENAME}"
    announce "Downloading wiremock"
    if [ -f "$FILEPATH" ]; then
        echo " already there"
    else
        echo -n " from ${url}"
        (
            cd "$SCRIPT_DIR"
            curl -q --fail -O "$url" > "$TMP_LOG"
        )
        echo " done"
        rm "$TMP_LOG"
    fi
}

is_running() {
    local pid

    if [[ ! -f "$PID_FILE" ]]; then
        return 1
    fi

    read -r pid < "$PID_FILE"
    ps -p "$pid" > /dev/null
}

goal_start() {
    local port=${PORT:-3334}
    local root_dir=${ROOT_DIR:-$PWD}

    announce "Starting wiremock"

    if [[ ! -f "$FILEPATH" ]]; then
        echo "Run $0 install first"
        exit 1
    fi

    if is_running; then
        echo " another running instance found"
        exit 1
    fi

    java -jar "$FILEPATH" --verbose --port "$port" --root-dir "$root_dir" > "$TMP_LOG" &
    echo "$!" > "$PID_FILE"

    wait_for_server "http://localhost:${port}/__admin"
    echo " done"
}

goal_stop() {
    local pid

    announce "Stopping wiremock"
    if ! is_running; then
        echo " no running instance found, nothing to do"
        return
    fi

    read -r pid < "$PID_FILE"
    kill "$pid" > "$TMP_LOG"
    rm "$PID_FILE"

    echo " done"
    rm "$TMP_LOG"
}

main() {
    trap hint_at_logs EXIT

    if type -t "goal_$1" &>/dev/null; then
        "goal_$1"
    else
        echo "usage: $0 (start|stop|install)"
    fi
}

main "$@"
