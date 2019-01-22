#!/usr/bin/env bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly TMP_LOG="/tmp/run.$$.log"
readonly PORT="8111"
readonly BASE_URL="http://localhost:${PORT}"
readonly USER="admin"
readonly PASSWORD="admin"
readonly BASE_API_URL="http://${USER}:${PASSWORD}@localhost:${PORT}/httpAuth/app/rest"
readonly DATA_DIR="${SCRIPT_DIR}/data"

wait_for_server() {
    local url=$1
    echo -n " waiting for ${url}"
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
    if [[ "$?" -ne 0 && -f "$TMP_LOG" ]]; then
        echo
        echo "Logs are in ${TMP_LOG}"
    else
        rm -f "$TMP_LOG"
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
    local data_projects="${DATA_DIR}/config/projects/"
    mkdir -p "$data_projects"
    cp -R "${SCRIPT_DIR}/projects"/* "$data_projects"

    docker_compose up --no-start
}

start_server() {
    local check_path="${1:-/favicon.ico}"
    announce "Starting docker image"
    docker_compose up -d &>> "$TMP_LOG"

    wait_for_server "${BASE_URL}${check_path}"
    echo " done"
}

authorize_worker() {
    curl --fail -X PUT -H "Content-Type: text/plain" --data true "${BASE_API_URL}/agents/id:1/authorized" &>> "$TMP_LOG"
}

provision_teamcity() {
    announce "Please manually create the admin user via the web UI ${BASE_URL}, user '${USER}' with password '${PASSWORD}' (I'll wait)"
    wait_for_server "${BASE_API_URL}/server"
    echo "Thank you"
    authorize_worker
}

teamcity_queue_length() {
    curl --silent --fail "${BASE_API_URL}/buildQueue" -H "Accept: application/json" | \
        python -c "import json; import sys; print json.loads(sys.stdin.read())['count']"
}

build_queue_empty() {
    if [[ "$(teamcity_queue_length)" -eq 0 ]]; then
        return 0;
    else
        return 1;
    fi
}

run_build() {
    local buildId="$1"
    announce "Triggering build ${buildId}"
    curl --fail -X POST -H "Content-Type: application/xml"  --data "<build><buildType id=\"${buildId}\"/></build>" "${BASE_API_URL}/buildQueue" &>> "$TMP_LOG"

    sleep 2
    until build_queue_empty ; do
        printf '.'
        sleep 5
    done
    echo " done"
}

run_builds() {
    run_build "SimpleSetup_Test"
    run_build "SimpleSetup_Test"
    run_build "SimpleSetup_Test"
    run_build "SimpleSetup_RSpecJUnitXml"
    run_build "SimpleSetup_RSpec"
    run_build "SimpleSetup_SubProject_Test"
}

goal_start() {
    if ! container_exists; then
        announce "Provisioning docker image"
        echo
        provision_container
        start_server
        provision_teamcity
        run_builds
    else
        start_server "/login.html"
    fi
}

goal_stop() {
    announce "Stopping docker image"
    docker_compose stop &>> "$TMP_LOG"
    echo " done"
}

goal_destroy() {
    announce "Destroying docker container"
    docker_compose down &>> "$TMP_LOG"
    rm -rf "$DATA_DIR" &>> "$TMP_LOG"
    echo " done"
}

goal_purge() {
    announce "Purging docker images"
    docker images -q jetbrains/teamcity-server | xargs docker rmi &>> "$TMP_LOG"
    docker images -q jetbrains/teamcity-minimal-agent | xargs docker rmi &>> "$TMP_LOG"
    echo " done"
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
