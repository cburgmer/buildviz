#!/bin/bash

function anyBuild {
    START=$RANDOM
    END=$[ $START + ( $RANDOM % 100 ) ]
    if [[ $[ $RANDOM % 2 ] -eq 0 ]]; then
        OUTCOME="pass"
    else
        OUTCOME="fail"
    fi
    aBuild $START $END $OUTCOME
}

function aBrokenBuild {
    START=$RANDOM
    END=$[ $START + ( $RANDOM % 100 ) ]
    OUTCOME="fail"
    aBuild $START $END $OUTCOME
}

function aBuild {
    START=$1
    END=$2
    OUTCOME=$3
    echo '{"start": '$START', "end": '$END', "outcome": "'$OUTCOME'"}'
}

function send {
    JOB=$1
    BUILD=$2
    curl -H "Content-Type: application/json" --data @- -XPUT "http://localhost:3000/builds/${JOB}/${BUILD}"
}

anyBuild | send "someBuild" 1
anyBuild | send "someBuild" 2
anyBuild | send "someBuild" 3

anyBuild | send "anotherBuild" 1

anyBuild | send "yetAnotherBuild" 1
anyBuild | send "yetAnotherBuild" 2

aBrokenBuild | send "aBrokenBuild" 1
aBrokenBuild | send "aBrokenBuild" 2
aBrokenBuild | send "aBrokenBuild" 3
