#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly TMP_LOG="/tmp/run.$$.log"
readonly BASE_URL="http://localhost:8080"

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
    if [[ "$?" -ne 0 && -f "$TMP_LOG" ]]; then
        echo
        echo "Logs are in ${TMP_LOG}"
    fi
}

container_exists() {
    if [[ -z $(docker container ls -q -a --filter name=buildviz_jenkins_example) ]]; then
        return 1
    else
        return 0
    fi
}

jenkins_queue_length() {
    curl --silent http://localhost:8080/queue/api/json | python -c "import json; import sys; print len(json.loads(sys.stdin.read())['items'])"
}

build_queue_empty() {
    if [[ "$(jenkins_queue_length)" -eq 0 ]]; then
        return 0;
    else
        return 1;
    fi
}

configure_pipeline() {
    local job_config
    local job_name
    local view_config
    local view_name
    (
        cd "$SCRIPT_DIR"/jobs
        for job_config in *; do
            # shellcheck disable=SC2001
            job_name=$( echo "$job_config" | sed s/.xml$// )
            curl --fail --silent -X POST --data-binary "@$job_config" -H "Content-Type: application/xml" "${BASE_URL}/createItem?name=${job_name}" > /dev/null
            curl --fail --silent -X POST --data-binary "@$job_config" "${BASE_URL}/job/${job_name}/config.xml" > /dev/null
        done
    )
    (
        cd "$SCRIPT_DIR"/views
        for view_config in *; do
            # shellcheck disable=SC2001
            view_name=$( echo "$view_config" | sed s/.xml$// )
            curl --fail --silent -X POST --data-binary "@$view_config" -H "Content-Type: application/xml" "${BASE_URL}/createView?name=${view_name}" > /dev/null
            curl --fail --silent -X POST --data-binary "@$view_config" "${BASE_URL}/view/${view_name}/config.xml" > /dev/null
        done
    )
}

trigger_builds() {
    for _ in 1 2 3 4 5; do
        curl --fail --silent -X POST "${BASE_URL}/job/Test/build" > /dev/null

        # Wait for build to run to enqueue next
        sleep 2
        until build_queue_empty ; do
            sleep 1
        done
    done
}

provision_jenkins() {
    docker build "$SCRIPT_DIR" --tag buildviz_jenkins_example
    docker container create -p 8080:8080 --name buildviz_jenkins_example buildviz_jenkins_example
    docker container start buildviz_jenkins_example

    echo "Disabling Jenkins security"
    wait_for_server "$BASE_URL/favicon.ico"
    sleep 10
    docker container exec buildviz_jenkins_example rm /var/jenkins_home/config.xml
    docker container exec buildviz_jenkins_example cp -p /var/jenkins_home/jenkins.install.UpgradeWizard.state /var/jenkins_home/jenkins.install.InstallUtil.lastExecVersion
    docker container restart buildviz_jenkins_example
    wait_for_server "$BASE_URL"

    echo "Configuring pipeline"
    configure_pipeline
    echo "Triggering builds"
    trigger_builds
}

goal_start() {
    if ! container_exists; then
        announce "Provisioning docker image"
        echo
        provision_jenkins
        echo "done"
    else
        announce "Starting docker image"
        docker container start buildviz_jenkins_example > "$TMP_LOG"

        wait_for_server "$BASE_URL"
        echo " done"
        rm "$TMP_LOG"
    fi
}

goal_stop() {
    announce "Stopping docker image"
    docker container stop buildviz_jenkins_example > "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

goal_destroy() {
    announce "Destroying docker container"
    docker container stop buildviz_jenkins_example > "$TMP_LOG"
    docker container rm buildviz_jenkins_example >> "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

goal_purge() {
    announce "Purging docker images"
    docker rmi jenkins/jenkins:2.128-alpine >> "$TMP_LOG"
    docker rmi buildviz_jenkins_example >> "$TMP_LOG"
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
