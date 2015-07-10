#!/bin/bash


function anyBuild {
    START=$[ $RANDOM * 60 * 60 * 1000 / 32767]
    DURATION=$[ $RANDOM * 60 * 60 * 1000 / 32767]
    END=$[ $START + $DURATION ]
    if [[ $[ $RANDOM % 2 ] -eq 0 ]]; then
        OUTCOME="pass"
    else
        OUTCOME="fail"
    fi
    REVISION=$[ $RANDOM % 4 ]
    aBuild $START $END $OUTCOME $REVISION
}

function aBrokenBuild {
    START=$[ $RANDOM * 60 * 60 * 1000 / 32767]
    DURATION=$[ $RANDOM * 60 * 60 * 1000 / 32767]
    END=$[ $START + $DURATION ]
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

function passingTestCase {
    cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="Test Suite">
    <testcase classname="A Class" name="A Test Case" time="0.0042">
    </testcase>
  </testsuite>
</testsuites>
EOF
}

function failingTestCase {
    cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="Test Suite">
    <testcase classname="A Class" name="A Test Case" time="0.0062">
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
    <testcase classname="A Class" name="Another Test Case" time="0.0021">
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

anyBuild | send "passingBuild" 1
anyBuild | send "passingBuild" 2
anyBuild | send "passingBuild" 3
passingTestCase | sendTestResult "passingBuild" 1
passingTestCase | sendTestResult "passingBuild" 2
passingTestCase | sendTestResult "passingBuild" 3

anyBuild | send "anotherBuild" 1

anyBuild | send "yetAnotherBuild" 1
anyBuild | send "yetAnotherBuild" 2

aBrokenBuild | send "aBrokenBuild" 1
aBrokenBuild | send "aBrokenBuild" 2
aBrokenBuild | send "aBrokenBuild" 3
failingTestCase | sendTestResult "aBrokenBuild" 1
anotherFailingTestCase | sendTestResult "aBrokenBuild" 2
anotherFailingTestCase | sendTestResult "aBrokenBuild" 3


aBuild 0 200000 'fail' 'abcd' | send "aFlakyBuild" 1
failingTestCase | sendTestResult "aFlakyBuild" 1
aBuild 1000000 1200000 'pass' 'abcd' | send "aFlakyBuild" 2
passingTestCase | sendTestResult "aFlakyBuild" 2
