#!/bin/bash

TODAY=$(date +%s000)
TWO_WEEKS_AGO=$[ $TODAY - 2 * 7 * 24 * 60 * 60 * 1000 ]

function anyBuild {
    START=$[ $TODAY - ( $RANDOM * 10000 ) ]
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

function aPassingBuild {
    START=$[ $TODAY - ( $RANDOM * 10000 ) ]
    DURATION=$[ $RANDOM * 60 * 60 * 1000 / 32767]
    END=$[ $START + $DURATION ]
    OUTCOME="pass"
    aBuild $START $END $OUTCOME
}

function aBrokenBuild {
    START=$[ $TODAY - ( $RANDOM * 100 ) ] # Make broken builds start later
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

aPassingBuild | send "passingBuild" 1
aPassingBuild | send "passingBuild" 2
aPassingBuild | send "passingBuild" 3
passingTestCase | sendTestResult "passingBuild" 1
passingTestCase | sendTestResult "passingBuild" 2
passingTestCase | sendTestResult "passingBuild" 3

for i in $(seq 1 20); do
    anyBuild | send "anotherBuild" "$i"
done

for i in $(seq 1 20); do
    anyBuild | send "yetAnotherBuild" "$i"
done

aBrokenBuild | send "aBrokenBuild" 1
aBrokenBuild | send "aBrokenBuild" 2
aBrokenBuild | send "aBrokenBuild" 3
failingTestCase | sendTestResult "aBrokenBuild" 1
anotherFailingTestCase | sendTestResult "aBrokenBuild" 2
anotherFailingTestCase | sendTestResult "aBrokenBuild" 3


aBuild $TWO_WEEKS_AGO $[ $TWO_WEEKS_AGO + 200000 ] 'fail' 'abcd' | send "aFlakyBuild" 1
failingTestCase | sendTestResult "aFlakyBuild" 1
aBuild $[ $TWO_WEEKS_AGO + 4000000 ] $[ $TWO_WEEKS_AGO + 4800000 ] 'pass' 'abcd' | send "aFlakyBuild" 2
passingTestCase | sendTestResult "aFlakyBuild" 2
