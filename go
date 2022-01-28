#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly SCRIPT_DIR

goal_install() {
    npm i
    npm run build
    ./lein deps
}

goal_prettier() {
    npm run prettier
}

goal_lint() {
    find "$SCRIPT_DIR" -name "*.sh" -not -path "${SCRIPT_DIR}/node_modules/*" -exec shellcheck {} +
    shellcheck "$SCRIPT_DIR"/go

    npm run lint
}

goal_test_unit() {
    # shellcheck disable=SC1010
    "${SCRIPT_DIR}/lein" do clean, test
}

goal_test_integration() {
    echo
    echo "Running integration test against recorded endpoints."
    echo "If this fails you might have changed how the endpoints are requested, and might want to record from scratch."
    echo "Testing buildviz.go.sync"
    "${SCRIPT_DIR}/test/integration/test_gocd.sh"
    echo "Testing buildviz.jenkins.sync"
    "${SCRIPT_DIR}/test/integration/test_jenkins.sh"
    echo "Testing buildviz.teamcity.sync"
    "${SCRIPT_DIR}/test/integration/test_teamcity.sh"
}

goal_test_example() {
    echo
    echo "Running simple example to make sure it doesn't break"
    yes | "${SCRIPT_DIR}/examples/runSeedDataExample.sh"
}

goal_audit() {
    npm audit --production
    "${SCRIPT_DIR}/lein" nvd check
}

goal_test() {
    goal_lint
    goal_test_unit
    goal_test_integration
    goal_test_example
    goal_audit
}

goal_make_release() {
    local NEW_VERSION=$1
    local OLD_VERSION

    if [ -z "$NEW_VERSION" ]; then
        echo "Provide a new version number"
        exit 1
    fi

    (
        cd "$SCRIPT_DIR"

        OLD_VERSION=$(git tag --sort=-version:refname | head -1)

        sed -i "" "s/$OLD_VERSION/$NEW_VERSION/g" README.md
        sed -i "" "s/buildviz \"$OLD_VERSION\"/buildviz \"$NEW_VERSION\"/" project.clj

        # shellcheck disable=SC1010
        goal_install # force package-lock.json to be updated now

        git add README.md project.clj package-lock.json
        git commit -m "Bump version"

        ./lein clean
        ./lein uberjar

        git show
        git tag "$NEW_VERSION"
        echo
        echo "You now want to"
        echo "$ git push origin master --tags"
        echo "and upload the jar, and finally"
        echo "$ ./go publish_docker ${NEW_VERSION}"
    )
}

fetch_buildviz_jar() {
    local version="$1"
    (
        cd src/docker
        rm -f buildviz-*-standalone.jar
        curl -LO "https://github.com/cburgmer/buildviz/releases/download/${version}/buildviz-${version}-standalone.jar"
    )
}

goal_publish_docker() {
    local latest_version="$1"
    local image_name="cburgmer/buildviz"

    if [[ -z "$latest_version" ]]; then
        echo "Please provide a version number"
        exit 1
    fi

    echo "Logged into docker registry?"
    read -rn 1

    fetch_buildviz_jar "$latest_version"
    docker build src/docker --tag "${image_name}:${latest_version}" --tag "${image_name}:latest"
    docker push "${image_name}:${latest_version}"
    docker push "${image_name}:latest"
}

goal_help() {
    local GOALS
    GOALS=$(set | grep -e "^goal_" | sed "s/^goal_\(.*\)().*/\1/" | xargs | sed "s/ / | /g")
    echo "Usage: $0 [ ${GOALS} ]"
}

main() {
    local GOAL

    if [[ -z "$1" ]] || ! type -t "goal_$1" &>/dev/null; then
        goal_help
        exit 1
    fi

    GOAL="$1"
    shift

    "goal_${GOAL}" "$@"
}

main "$@"
