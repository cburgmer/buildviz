# buildviz

Transparency for your build pipeline's results and runtime.

![Screenshot](https://github.com/cburgmer/buildviz/raw/master/examples/data/screenshot.png)

## Concepts

buildviz looks from different angles at a build pipeline. So far it does only know about

* **jobs**, a job is part of the pipeline and executes some meaningful action,
* **builds**, a build is an instance of the job being triggered, it has a unique **id**, a **start** and **end time**, an **outcome** and possibly one or more **inputs** with a given **revision**,
* **test results**, a list of tests with **runtime** and **outcome**.

## Example

Seed dummy data for a quick impression

    $ ./examples/runSeedDataExample.sh

or run Go.cd in a Vagrant box and sync its job history with buildviz

    $ ./examples/runGoCdExample.sh

## Usage

    $ ./lein do deps, ring server-headless
    $ open http://localhost:3000/index.html

Then `PUT` JSON data in the following format to `http://localhost:3000/builds/$JOB_NAME/$BUILD_ID`

```js
{
"start": $START_TIMESTAMP,
"end": $END_TIMESTAMP,
"outcome": "pass", /* or "fail" */
"inputs": [{
    "revision": "$REVISION",
    "source_id": "$SOURCE_ID"
}]
}
```

You can omit any of the above values, however some of the statistics will not be available then.

JUnit XML formatted test results can be `PUT` to `http://localhost:3000/builds/$JOB_NAME/$BUILD_ID/testresults`

## Getting the data

Buildviz does not care where the data is coming from. The general idea is to PUT new outcomes to `/builds` whenever another build has run.

If you are using [Go.cd](http://www.go.cd), try the script `./scripts/gosync.clj` to sync existing history:

```sh
./lein exec scripts/gosync.clj http://$USER:$PW@localhost:8153/go
```

### More

[FAQ](https://github.com/cburgmer/buildviz/wiki)

Reach out to [@cburgmer](https://twitter.com/cburgmer) for feedback and ideas.
