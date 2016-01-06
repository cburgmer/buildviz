#!/bin/bash
set -e
VERSION="1.57"
FILENAME="wiremock-${VERSION}-standalone.jar"

if [ ! -f "${FILENAME}" ]; then
    curl -O "http://repo1.maven.org/maven2/com/github/tomakehurst/wiremock/${VERSION}/${FILENAME}"
fi

java -jar "${FILENAME}" --port 3334
