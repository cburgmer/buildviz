#!/bin/bash

function aBuild {
    START=$RANDOM
    END=$[ $START + ( $RANDOM % 100 ) ]
    if [[ $[ $RANDOM % 2 ] -eq 0 ]]; then
        OUTCOME="pass"
    else
        OUTCOME="fail"
    fi
    echo '{"start": '$START', "end": '$END', "outcome": "'$OUTCOME'"}'
}

function send {
    JOB=$1
    BUILD=$2
    curl -H "Content-Type: application/json" --data @- -XPUT "http://localhost:3000/builds/${JOB}/${BUILD}"
}

aBuild | send "someBuild" 1
aBuild | send "someBuild" 2
aBuild | send "someBuild" 3

aBuild | send "anotherBuild" 1

aBuild | send "yetAnotherBuild" 1
aBuild | send "yetAnotherBuild" 2
