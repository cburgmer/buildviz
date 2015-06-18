#!/bin/bash

function anyBuild {
    START=$RANDOM
    END=$[ $START + ( $RANDOM % 100 ) ]
    if [[ $[ $RANDOM % 2 ] -eq 0 ]]; then
        OUTCOME="pass"
    else
        OUTCOME="fail"
    fi
    REVISION=$[ $RANDOM % 4 ]
    aBuild $START $END $OUTCOME $REVISION
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
    REVISION=$4
    SOURCE_ID=42
    if [[ -z "$REVISION" ]]; then
        echo '{"start": '$START', "end": '$END', "outcome": "'$OUTCOME'"}'
    else
        echo '{"start": '$START', "end": '$END', "outcome": "'$OUTCOME'", "inputs": [{"revision": "'$REVISION'", "source_id": "'$SOURCE_ID'"}]}'
    fi
}

function send {
    JOB=$1
    BUILD=$2
    curl -H "Content-Type: application/json" --data @- -XPUT "http://localhost:3000/builds/${JOB}/${BUILD}"
}

function failingTestCase {
    cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="Test Suite">
    <testcase name="A Test Case" time="0.0062">
      <failure>Meh</failure>
    </testcase>
  </testsuite>
</testsuites>
EOF
}

function anotherFailingTestCase {
    cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="Test Suite">
    <testcase name="Another Test Case" time="0.0021">
      <failure>Meh</failure>
    </testcase>
  </testsuite>
</testsuites>
EOF
}

function sendTestResult {
    JOB=$1
    BUILD=$2
    curl -X PUT -d@- "http://localhost:3000/builds/${JOB}/${BUILD}/testresults"
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
failingTestCase | sendTestResult "aBrokenBuild" 1
anotherFailingTestCase | sendTestResult "aBrokenBuild" 2
anotherFailingTestCase | sendTestResult "aBrokenBuild" 3


aBuild 0 20 'fail' 'abcd' | send "aFlakyBuild" 1
aBuild 100 120 'pass' 'abcd' | send "aFlakyBuild" 2
