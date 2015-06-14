# buildviz

Transparency for your build pipeline's results and runtime.

**This is experimental, don't get your hopes up**

![Screenshot](https://github.com/cburgmer/buildviz/raw/master/screenshot.png)

## Motivation

The [Toyota Production System](http://en.wikipedia.org/wiki/Toyota_Production_System) advocates the ["jidoka"](http://en.wikipedia.org/w/index.php?title=Jidoka&redirect=no) principle where manual supervision steps in when an anomaly is detected, the production line is then stopped and finally the anomaly is removed. So far our software build pipeline follows the lean manufacturing metaphor.

Too often we seem to be forgetting though that this principle makes yet another step mandatory: *reflection* (["hansei"](http://en.wikipedia.org/wiki/Hansei)) including a root cause analysis that leads to the improvement of the production process (in the spirit of ["kaizen"](http://en.wikipedia.org/wiki/Kaizen)).

May this tool evolve towards supporting us in reflecting on our software production line.

## Usage

    $ ./lein ring server-headless
    $ open http://localhost:3000/index.html

Then `PUT` data to `http://localhost:3000/builds/$JOB_NAME/$BUILD_ID` 

Valid content can be

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
    
## Run as standalone jar

    $ ./lein ring uberjar
    $ java -jar target/buildviz-*-standalone.jar

## Example    

Seed dummy data for a quick impression.

    $ ./seedDummyData.sh

### Full fledged example

Install Go.cd

    $ vagrant init gocd/gocd-demo; vagrant box update; vagrant up

Start buildviz

    $ ./lein ring server-headless

Sync buildviz with the Go builds

    $ ./lein exec scripts/gosync.clj http://localhost:8153/go
