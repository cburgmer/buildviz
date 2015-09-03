#!/bin/bash

TODAY=$(date +%s000)
A_WEEK_AGO=$(( TODAY - 7 * 24 * 60 * 60 * 1000 ))


function aBuild {
    OUTCOME=$1
    if [ -z "$OUTCOME" ]; then
        if [ $(( RANDOM % 3 )) -eq 0 ]; then
            OUTCOME="fail"
        else
            OUTCOME="pass"
        fi
    fi

    START=$2
    if [ -z "$START" ]; then
        START=$(( TODAY - ( RANDOM * 10000 ) ))
    fi

    DURATION=$(( RANDOM * 10 ))
    END=$(( START + DURATION ))

    REVISION=$3
    if [ -z "$REVISION" ]; then
        REVISION=$(( RANDOM % 4 ))
    fi

    SOURCE_ID=42
    echo '{"start": '$START', "end": '$END', "outcome": "'$OUTCOME'", "inputs": [{"revision": "'$REVISION'", "source_id": "'$SOURCE_ID'"}]}'
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
    <testsuite name="Nesting 1">
      <testsuite name="Nesting 2">
        <testcase classname="A Class" name="A Test Case" time="0.0042"/>
      </testsuite>
    </testsuite>
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
    <testsuite name="Nested Test Suite">
      <testcase classname="Another Class" name="A Test Case" time="0.0062">
        <failure/>
      </testcase>
      <testcase classname="Another Class" name="Another Test Case" time="0.0050">
        <failure/>
      </testcase>
    </testsuite>
  </testsuite>
</testsuites>
EOF
}

function anotherFailingTestCase {
    cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="Another Test Suite">
    <testsuite name="Nested Test Suite">
      <testcase classname="A Class" name="Another Test Case" time="0.0021">
        <failure>Meh</failure>
      </testcase>
    </testsuite>
  </testsuite>
</testsuites>
EOF
}

function sendTestResult {
    JOB=$1
    BUILD=$2
    curl -X PUT -d@- "http://localhost:3000/builds/${JOB}/${BUILD}/testresults"
}

for i in $(seq 1 3); do
    aBuild "pass" | send "passingBuild" "$i"
    passingTestCase | sendTestResult "passingBuild" "$i"
done

for i in $(seq 1 20); do
    aBuild | send "anotherBuild" "$i"
done

for i in $(seq 1 15); do
    aBuild | send "yetAnotherBuild" "$i"
done

for i in $(seq 1 3); do
    aBuild "fail" $[ $TODAY - $i * 5000000 ] | send "aBrokenBuild" "$i"
done
failingTestCase | sendTestResult "aBrokenBuild" 1
anotherFailingTestCase | sendTestResult "aBrokenBuild" 2
anotherFailingTestCase | sendTestResult "aBrokenBuild" 3

aBuild 'fail' $A_WEEK_AGO "abcd" | send "aFlakyBuild" 1
failingTestCase | sendTestResult "aFlakyBuild" 1
aBuild 'pass' $(( A_WEEK_AGO + 8000000 )) "abcd" | send "aFlakyBuild" 2
passingTestCase | sendTestResult "aFlakyBuild" 2

echo '{}' | send "buildWithoutInfo" "1"
