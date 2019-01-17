#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly TMP_LOG="/tmp/run.$$.log"
readonly PORT="8111"
readonly BASE_URL="http://localhost:${PORT}"
readonly USER="admin"
readonly PASSWORD="admin"
readonly BASE_API_URL="http://${USER}:${PASSWORD}@localhost:${PORT}/httpAuth/app/rest"

wait_for_server() {
    local url=$1
    echo -n " waiting for ${url}"
    until curl --output /dev/null --silent --head --fail "$url"; do
        printf '.'
        sleep 5
    done
}

wait_for_server_200() {
    local url=$1
    echo -n " waiting for ${url} to return 200"
    until [[ $(curl  --output /dev/null --silent --head --write-out "%{http_code}" "$url") -eq 200 ]]; do
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

provision_container() {
    docker_compose up --no-start
}

start_server() {
    announce "Starting docker image"
    docker_compose up -d &> "$TMP_LOG"

    wait_for_server "${BASE_URL}/mnt"
    echo " done"
    rm "$TMP_LOG"
}

authorize_worker() {
    curl -X PUT -H "Content-Type: text/plain" --data true "${BASE_API_URL}/agents/id:1/authorized" &> "$TMP_LOG"
    rm "$TMP_LOG"
}

run_build() {
    local buildId="$1"
    curl -X POST -H "Content-Type: application/xml"  --data "<build><buildType id=\"${buildId}\"/></build>" "${BASE_API_URL}/buildQueue"
}

run_builds() {
    announce "Running builds"
    run_build "SimpleSetup_Test" &> "$TMP_LOG"
    run_build "SimpleSetup_RSpecJUnitXml" &> "$TMP_LOG"
    run_build "SimpleSetup_RSpec" &> "$TMP_LOG"
    run_build "SimpleSetup_SubProject_Test" &> "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

provision_teamcity() {
    announce "Please manually create the admin user via the web UI ${BASE_URL}, user '${USER}' with password '${PASSWORD}' (I'll wait)"
    wait_for_server_200 "${BASE_API_URL}/server"
    echo "Thank you"
    authorize_worker
    run_builds
}

goal_start() {
    if ! container_exists; then
        announce "Provisioning docker image"
        echo
        provision_container
        start_server
        provision_teamcity
    else
        start_server
    fi
}

goal_stop() {
    announce "Stopping docker image"
    docker_compose stop &> "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

goal_destroy() {
    announce "Destroying docker container"
    docker_compose down &> "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

goal_purge() {
    announce "Purging docker images"
    docker rmi jetbrains/teamcity-server:2018.1.2 &> "$TMP_LOG"
    docker rmi jetbrains/teamcity-minimal-agent:2018.1.2 &> "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

main() {
    trap hint_at_logs EXIT

    if type -t "goal_$1" &>/dev/null; then
        "goal_$1"
    else
        echo "usage: $0 (start|stop|destroy|purge)"
    fi
}

main "$@"
