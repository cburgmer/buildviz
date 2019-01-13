#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly TMP_LOG="/tmp/run.$$.log"
readonly BASE_URL="http://localhost:8111/mnt"

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

docker_compose() {
    (
        cd "$SCRIPT_DIR"
        docker-compose "$@"
    )
}

container_exists() {
    if [[ -z $(docker_compose ps -q) ]]; then
        return 1
    else
        return 0
    fi
}

provision_teamcity() {
    docker_compose up --no-start
}

start_teamcity() {
    announce "Starting docker image"
    docker_compose up -d &> "$TMP_LOG"

    wait_for_server "$BASE_URL"
    echo " done"
    rm "$TMP_LOG"
}

goal_start() {
    local run
    if ! container_exists; then
        announce "Provisioning docker image"
        echo
        provision_teamcity
        start_teamcity
    else
        start_teamcity
    fi
}

goal_stop() {
    announce "Stopping docker image"
    docker_compose stop &> "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

main() {
    trap hint_at_logs EXIT

    if type -t "goal_$1" &>/dev/null; then
        "goal_$1"
    else
        echo "usage: $0 (start|stop)"
    fi
}

main "$@"
