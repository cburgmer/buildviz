# buildviz

Transparency for your build pipeline's results and runtime.

![Screenshot](https://github.com/cburgmer/buildviz/raw/master/examples/data/screenshot.png)

## Usage

    $ ./lein npm install
    $ ./lein ring server-headless
    $ open http://localhost:3000/index.html

Then `PUT` data to `http://localhost:3000/builds/$JOB_NAME/$BUILD_ID`

Valid content (JSON) can be

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

All values are optional.

JUnit XML output can be `PUT` to `http://localhost:3000/builds/$JOB_NAME/$BUILD_ID/testresults`

## Getting the data

buildviz does not care where the data is coming from. It expects to be told via PUTs against `/builds`.

If you are using Go.cd, try the script `./scripts/gosync.clj`:

```sh
./lein exec scripts/gosync.clj http://$USER:$PW@localhost:8153/go $PIPELINE_GROUP $ANOTHER_PIPELINE_GROUP...
```

## Example

Seed dummy data for a quick impression

    $ ./examples/runSeedDataExample.sh

or run Go.cd in a Vagrant box and sync its job history with buildviz

    $ ./examples/runGoCdExample.sh

### More

[FAQ](https://github.com/cburgmer/buildviz/wiki)

Maintained by [@cburgmer](https://twitter.com/cburgmer).
