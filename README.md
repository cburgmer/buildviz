# buildviz

Transparency for your build pipeline's results and runtime.

> The most important things cannot be measured.
> - [W. Edwards Deming](https://en.wikipedia.org/wiki/W._Edwards_Deming)
>
> > Your build pipeline can.
> > - Anonymous

## The What

Buildviz provides graphs detailing runtime behaviour, failures and stability of the pipeline, answering a
[multitude of questions](https://github.com/cburgmer/buildviz/wiki/Questions) in the hopes of improving your pipeline.

All it needs is your build history including test results.

![Screenshot](https://github.com/cburgmer/buildviz/raw/master/examples/data/screenshot.png)

## Usage

    $ curl -OL https://github.com/cburgmer/buildviz/releases/download/0.15.0/buildviz-0.15.0-standalone.jar
    $ java -jar buildviz-0.15.0-standalone.jar

Now, buildviz takes in new build results via `PUT` to `/builds`. Some suggestions how to set it up:

#### DIY

For every build `PUT` JSON data to `http://localhost:3000/builds/$JOB_NAME/$BUILD_ID`, for example:

```js
{
  "start": 1451449853542,
  "end": 1451449870555,
  "outcome": "pass", /* or "fail" */
  "inputs": [{
    "revision": "1eadcdd4d35f9a",
    "sourceId": "git@github.com:cburgmer/buildviz.git"
  }],
  "triggeredBy": [{
    "jobName": "Test",
    "buildId": "42"
  }]
}
```

The build's `start` is required, all other values are optional.

JUnit XML ([or JSON](https://github.com/cburgmer/buildviz/wiki#help-my-tests-dont-generate-junit-xml)) formatted test results can be `PUT` to `http://localhost:3000/builds/$JOB_NAME/$BUILD_ID/testresults`

#### Sync from supported build servers

E.g. sync existing history from GoCD (see `--help` for details):

    $ java -cp buildviz-0.15.0-standalone.jar buildviz.go.sync http://$USER:$PW@localhost:8153/go

There's support for [Jenkins, GoCD and TeamCity](https://github.com/cburgmer/buildviz/wiki/CI-tool-integration).

## More

[FAQ](https://github.com/cburgmer/buildviz/wiki)

You might also like:

* [Metrik](https://github.com/thoughtworks/metrik), calculates the [4 key metrics](https://www.thoughtworks.com/radar/techniques/four-key-metrics) based on CI/CD build data.
* [HeartBeat](https://github.com/thoughtworks/HeartBeat), calculates delivery metrics from CI/CD build data, revision control and project planning tools.
* [Kuona project for IT Analytics](https://github.com/kuona/kuona-project), provides a dashboard on data from various sources.
* [Test Trend Analyzer](https://github.com/anandbagmar/tta), consumes test results for test trends.
* [TRT](https://github.com/thetestpeople/trt), consumes test results for test trends.
* [GoCD's analytics extension](https://extensions-docs.gocd.org/analytics/current/), collects and displays build metrics for GoCD.
* [BuildPulse](https://github.com/marketplace/buildpulse), automatically detects flaky tests.

Reach out to [@cburgmer](https://twitter.com/cburgmer) for feedback and ideas.
