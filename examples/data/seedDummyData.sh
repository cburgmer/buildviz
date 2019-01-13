#!/bin/bash

set -e

TARGET_PORT=${PORT:=3000}

TODAY=$(date +%s000)
YESTERDAY=$(( TODAY - 1 * 24 * 60 * 60 * 1000 ))
SOME_DAYS_AGO=$(( TODAY - 3 * 24 * 60 * 60 * 1000 ))
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

    TRIGGERED_BY_JOB=$2
    TRIGGERED_BY_BUILD_ID=$3

    if [ -n "$TRIGGERED_BY_JOB" ]; then
        # shellcheck disable=SC2089
        TRIGGERED_BY=', "triggeredBy": [{"jobName": "'"$TRIGGERED_BY_JOB"'", "buildId":"'"$TRIGGERED_BY_BUILD_ID"'"}]'
    fi

    START=$4
    if [ -z "$START" ]; then
        START=$(( TODAY - ( RANDOM * 10000 ) ))
    fi

    DURATION=$(( RANDOM * 10 ))
    END=$(( START + DURATION ))

    REVISION=$5
    if [ -z "$REVISION" ]; then
        REVISION=$(( RANDOM % 4 ))
    fi

    SOURCE_ID=42
    echo '{"start": '"$START"', "end": '"$END"', "outcome": "'"$OUTCOME"'", "inputs": [{"revision": "'"$REVISION"'", "sourceId": "'"$SOURCE_ID"'"}]'"$TRIGGERED_BY"'}'
}

function send {
    JOB=$1
    BUILD=$2
    curl --silent -H "Content-Type: application/json" --data @- -XPUT "http://localhost:${TARGET_PORT}/builds/${JOB}/${BUILD}" > /dev/null
}

function passingTestCase {
    cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="Test Suite">
    <testsuite name="Nesting 1">
      <testsuite name="Nesting 2">
        <testcase classname="some.package.with.class" name="A Test Case" time="0.0042"/>
        <testcase classname="some.package.another_class" name="Another Test Case" time="0.0030"/>
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
    <testcase classname="a.package.class" name="A Test Case" time="0.0062">
      <failure>Meh</failure>
    </testcase>
    <testcase classname="a.package.another_class" name="Some Test Case" time="0.0060">
    </testcase>
    <testsuite name="Nested Test Suite">
      <testcase classname="another.package.class" name="A Test Case" time="0.0062">
        <failure/>
      </testcase>
      <testcase classname="another.package.class" name="Another Test Case" time="0.0050">
        <failure/>
      </testcase>
    </testsuite>
  </testsuite>
</testsuites>
EOF
}

function anotherTestCase {
    RESULT=$1
    if [ "$RESULT" == "fail" ]; then
        TESTCASE="<failure>Meh</failure>"
    else
        TESTCASE=''
    fi

    TESTNAME=$2
    if [ -z "$TESTNAME" ]; then
        TESTNAME="Another Test Case"
    fi

    cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="Another Test Suite">
    <testsuite name="Nested Test Suite">
      <testcase classname="some.class" name="$TESTNAME" time="0.0021">
        $TESTCASE
      </testcase>
    </testsuite>
  </testsuite>
</testsuites>
EOF
}

function aTestCaseWithoutRuntime {
    cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="Test Suite">
    <testcase classname="a.class" name="a_test">
    </testcase>
  </testsuite>
</testsuites>
EOF
}

function sendTestResult {
    JOB=$1
    BUILD=$2
    curl -X PUT -H "Content-type: text/xml" -d@- "http://localhost:${TARGET_PORT}/builds/${JOB}/${BUILD}/testresults"
}

for i in $(seq 1 20); do
    aBuild '' '' '' $(( SOME_DAYS_AGO - ( RANDOM * 10000 ) )) | send "anotherBuild" "$i"
done

for i in $(seq 1 15); do
    aBuild '' 'anotherBuild' "$i" $(( YESTERDAY - ( RANDOM * 10000 ) )) | send "yetAnotherBuild" "$i"
done

for i in $(seq 1 3); do
    aBuild "pass" 'yetAnotherBuild' "$i" | send "passingBuild" "$i"
    passingTestCase | sendTestResult "passingBuild" "$i"
done

for i in $(seq 1 3); do
    aBuild "fail" '' '' $(( TODAY - i * 5000000 )) | send "aBrokenBuild" "$i"
done
failingTestCase | sendTestResult "aBrokenBuild" 1
anotherTestCase "fail" | sendTestResult "aBrokenBuild" 2
anotherTestCase "fail" | sendTestResult "aBrokenBuild" 3

aBuild 'fail' '' '' $A_WEEK_AGO "abcd" | send "aFlakyBuild" 1
failingTestCase | sendTestResult "aFlakyBuild" 1
aBuild 'pass' '' '' $(( A_WEEK_AGO + 8000000 )) "abcd" | send "aFlakyBuild" 2
passingTestCase | sendTestResult "aFlakyBuild" 2
aBuild 'fail' '' '' '' "xyz" | send "aFlakyBuild" 3
anotherTestCase "fail" | sendTestResult "aFlakyBuild" 3
aBuild 'fail' '' '' '' "xyz" | send "aFlakyBuild" 4
anotherTestCase "fail" | sendTestResult "aFlakyBuild" 4
aBuild 'pass' '' '' '' "xyz" | send "aFlakyBuild" 5
anotherTestCase "pass" | sendTestResult "aFlakyBuild" 5
aBuild 'fail' '' '' '' "123" | send "aFlakyBuild" 6
anotherTestCase "fail" "test" | sendTestResult "aFlakyBuild" 6
aBuild 'pass' '' '' '' "123" | send "aFlakyBuild" 7
anotherTestCase "pass" "test" | sendTestResult "aFlakyBuild" 7


echo "{\"start\": $TODAY}" | send "buildWithoutInfo" "1"

echo "{\"start\": $TODAY}" | send "buildWithTestWithoutRuntime" "1"
aTestCaseWithoutRuntime | sendTestResult "buildWithTestWithoutRuntime" "1"
