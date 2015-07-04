# buildviz

Transparency for your build pipeline's results and runtime.

![Screenshot](https://github.com/cburgmer/buildviz/raw/master/examples/data/screenshot.png)

## Example

Seed dummy data for a quick impression

    $ ./examples/runSeedDataExample.sh

or run Go.cd in a Vagrant box and sync its job history with buildviz

    $ ./examples/runGoCdExample.sh

## Usage

    $ ./lein npm install
    $ ./lein ring server-headless
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
./lein exec scripts/gosync.clj http://$USER:$PW@localhost:8153/go $PIPELINE_GROUP $ANOTHER_PIPELINE_GROUP...
```

### More

[FAQ](https://github.com/cburgmer/buildviz/wiki)

Reach out to [@cburgmer](https://twitter.com/cburgmer) for feedback and ideas.
