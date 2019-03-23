#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)

readonly BUILDVIZ_PORT=3333
readonly BUILDVIZ_PATH="http://localhost:${BUILDVIZ_PORT}"

ensure_port_available() {
    if curl --output /dev/null --silent --head --fail "$BUILDVIZ_PATH"; then
        echo "Please stop the application running on port ${PORT} before continuing"
        exit 1
    fi
}

clean_up() {
    "${SCRIPT_DIR}/data/run_buildviz.sh" stop
}

main() {
    ensure_port_available

    # Handle Ctrl+C
    trap clean_up EXIT

    PORT="$BUILDVIZ_PORT" "$SCRIPT_DIR"/data/run_buildviz.sh start

    echo "Seeding data..."
    PORT="$BUILDVIZ_PORT" "$SCRIPT_DIR"/data/seedDummyData.sh

    echo "Done..."
    echo
    echo "Point your browser to ${BUILDVIZ_PATH}"
    echo
    echo "Later, press any key to stop the server"

    read -rn 1
}

main
