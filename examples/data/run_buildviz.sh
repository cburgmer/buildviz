#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly TMP_LOG="/tmp/run.$$.log"
readonly BUILDVIZ_TMP_DATA_DIR="/tmp/record.buildviz.$$"
readonly PID_FILE="${SCRIPT_DIR}/buildviz.pid"

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
    if [[ "$?" -ne 0 ]]; then
        echo
        echo "Logs are in ${TMP_LOG}"
    fi
}

goal_start() {
    local port=${PORT:-3334}

    announce "Starting buildviz"
    mkdir "$BUILDVIZ_TMP_DATA_DIR"

    BUILDVIZ_DATA_DIR="$BUILDVIZ_TMP_DATA_DIR" "${SCRIPT_DIR}/../../lein" do deps, ring server-headless "$port" > "$TMP_LOG" &
    echo "$!" > "$PID_FILE"

    wait_for_server "http://localhost:${port}"
    echo " done"
    rm "$TMP_LOG"
}

goal_stop() {
    local pid

    announce "Stopping buildviz"
    if [[ ! -f "$PID_FILE" ]]; then
        echo " no PID file found, nothing to do"
        return
    fi

    read -r pid < "$PID_FILE"

    pkill -P "$pid" > "$TMP_LOG"
    rm "$PID_FILE"
    rm -rf "$BUILDVIZ_TMP_DATA_DIR"

    echo " done"
    rm "$TMP_LOG"
}

main() {
    trap hint_at_logs EXIT

    if type -t "goal_$1" &>/dev/null; then
        goal_$1
    else
        echo "usage: $0 (start|stop)"
    fi
}

main "$@"
