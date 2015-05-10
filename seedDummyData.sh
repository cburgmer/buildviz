#!/bin/sh

function build {
    START=$1
    END=$2
    echo '{"start":' $START ', "end":' $END '}'
}

function send {
    JOB=$1
    BUILD=$2
    curl -H "Content-Type: application/json" --data @- -XPUT "http://localhost:3000/builds/${JOB}/${BUILD}"
}

build 0 3 | send "someBuild" 1
build 10 30 | send "someBuild" 2
build 35 40 | send "someBuild" 3

build 100 200 | send "anotherBuild" 1

build 0 10 | send "yetAnotherBuild" 1
build 20 30 | send "yetAnotherBuild" 2
