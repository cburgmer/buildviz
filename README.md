# buildviz

Transparency for your build pipeline's results and runtime.

> The most important things cannot be measured.
> - [W. Edwards Deming](https://en.wikipedia.org/wiki/W._Edwards_Deming)
>
> > Your build pipeline can.
> > - Anonymous

## Concepts

buildviz provides various graphs detailing your build pipeline's behaviour. So far it cares about

* **jobs**, a job is part of the pipeline and executes some meaningful action,
* **builds**, a build is an instance of the job being triggered, it has a unique **id**, a **start** and **end time**, an **outcome** and possibly one or more **inputs** with a given **revision**,
* **test results**, a list of tests with **runtime** and **status**.

## Example

![Screenshot](https://github.com/cburgmer/buildviz/raw/master/examples/data/screenshot.png)

Seed dummy data for a quick impression

    $ ./examples/runSeedDataExample.sh

## Usage

    $ curl -OL https://github.com/cburgmer/buildviz/releases/download/0.7.0/buildviz-0.7.0-standalone.jar
    $ java -jar buildviz-0.7.0-standalone.jar

Now, buildviz takes in new build results via `PUT` to `/builds`. Some suggestions how to set it up:

#### DIY

For every build `PUT` JSON data in the following format to `http://localhost:3000/builds/$JOB_NAME/$BUILD_ID`

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

The build `start` is required, all other values are optional.

JUnit XML ([or JSON](https://github.com/cburgmer/buildviz/wiki#help-my-tests-dont-generate-junit-xml)) formatted test results can be `PUT` to `http://localhost:3000/builds/$JOB_NAME/$BUILD_ID/testresults`

#### Sync from [Go.cd](http://www.go.cd)

Sync existing history (see `--help` for details):

    $ java -cp buildviz-0.7.0-standalone.jar buildviz.go.sync http://$USER:$PW@localhost:8153/go

## More

[FAQ](https://github.com/cburgmer/buildviz/wiki)

You might also like [Kuona - Delivery Dashboard Generator](https://github.com/kuona/kuona) or [Test Trend Analyzer](https://github.com/anandbagmar/tta).

Reach out to [@cburgmer](https://twitter.com/cburgmer) for feedback and ideas.
