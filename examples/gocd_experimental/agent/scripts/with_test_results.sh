#!/bin/bash
set -eo pipefail

is_successful() {
    local outcome="$1"
    test "$outcome" -ne 0
}

test_results() {
    local outcome="$1"
    local failure=""
    local error=""

    if ! is_successful "$outcome"; then
        failure="<failure>Meh</failure>"
        error="<error>Argh</error>"
    fi

    cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="Another Test Suite">
    <testsuite name="Nested Test Suite">
      <testcase classname="some.class" name="A Test" time="0.0021">
        ${failure}
      </testcase>
      <testcase classname="some.class" name="Some Test" time="0.005">
        ${error}
      </testcase>
      <testcase classname="some.class" name="Another Test" time="0.003">
      </testcase>
      <testcase classname="some.class" name="Skipped Test" time="0.004">
          <skipped/>
      </testcase>
    </testsuite>
  </testsuite>
</testsuites>
EOF
}

main() {
    local outcome
    outcome=$((GO_PIPELINE_COUNTER % 3))

    test_results "$outcome" > results.xml
    is_successful "$outcome"
}

main "$@"
