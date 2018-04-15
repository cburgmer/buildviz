#!/bin/bash
set -e
VERSION="2.16.0"
ARTIFACT_NAME="wiremock-standalone"
FILENAME="${ARTIFACT_NAME}-${VERSION}.jar"

if [ ! -f "${FILENAME}" ]; then
    curl -O "http://repo1.maven.org/maven2/com/github/tomakehurst/${ARTIFACT_NAME}/${VERSION}/${FILENAME}"
fi

java -jar "${FILENAME}" --port 3334
