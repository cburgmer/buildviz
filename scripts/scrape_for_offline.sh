#!/bin/bash
set -e

BUILDVIZ_URL=${1:-"http://localhost:3000"}

DOWNLOAD_DIR="/tmp/buildviz_scraped_$$"

mkdir "$DOWNLOAD_DIR"
cd "$DOWNLOAD_DIR"

wget --no-verbose -r --no-host-directories "$BUILDVIZ_URL/index.html"

for RESOURCE in status jobs pipelineruntime jobruntime waittimes failphases testcases testclasses flakytestcases ; do
    wget --no-verbose "$BUILDVIZ_URL/$RESOURCE.json" -O "$RESOURCE"
    wget --no-verbose "$BUILDVIZ_URL/$RESOURCE.csv" -O "$RESOURCE.csv"
done

echo "Downloaded into $DOWNLOAD_DIR"
