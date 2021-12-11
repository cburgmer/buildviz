#!/bin/bash
set -eo pipefail

readonly PORT=${1:-3334}
readonly ROOT_DIR=${2:-$PWD}

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly SCRIPT_DIR

readonly VERSION="2.16.0"
readonly ARTIFACT_NAME="wiremock-standalone"
readonly FILENAME="${ARTIFACT_NAME}-${VERSION}.jar"
readonly FILEPATH="${SCRIPT_DIR}/${FILENAME}"

if [ ! -f "$FILEPATH" ]; then
    echo "Downloading wiremock"
    (
        cd "$SCRIPT_DIR"
        curl -O "http://repo1.maven.org/maven2/com/github/tomakehurst/${ARTIFACT_NAME}/${VERSION}/${FILENAME}"
    )
fi

java -jar "$FILEPATH" --port "$PORT" --root-dir "$ROOT_DIR"> /dev/null
