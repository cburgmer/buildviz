# buildviz

Transparency for your build pipeline's results and runtime.

> The most important things cannot be measured.
> - [W. Edwards Deming](https://en.wikipedia.org/wiki/W._Edwards_Deming)
>
> > Your build pipeline can.
> > - Anonymous

## The What

Buildviz provides graphs detailing runtime behaviour, failures and stability of
the pipeline, answering a [multitude of questions](https://github.com/cburgmer/buildviz/wiki/Questions)
in the hopes of improving your pipeline.

All it needs is your build history including test results.

Live example: https://buildviz.cburgmer.space/

![Screenshot](https://github.com/cburgmer/buildviz/raw/master/examples/data/screenshot.png)

## Usage

    $ curl -OL https://github.com/cburgmer/buildviz/releases/download/0.15.1/buildviz-0.15.1-standalone.jar
    $ java -jar buildviz-0.15.1-standalone.jar

Now, buildviz takes in build data via `POST` to `/builds`. Some suggestions how
to set it up:

#### Sync from supported build servers

Buildviz understands build data extracted by [build-facts](https://github.com/cburgmer/build-facts).
Currently the following CI/CD systems are [supported](https://github.com/cburgmer/build-facts#supported-build-servers):

- Concourse
- GoCD
- Jenkins
- TeamCity

Example:

    # After starting up Buildviz locally, do:
    $ curl -LO https://github.com/cburgmer/build-facts/releases/download/0.5.4/build-facts-0.5.4-standalone.jar
    $ java -jar build-facts-0.5.4-standalone.jar jenkins http://localhost:8080 --state state.json \
        | curl -v -H "Content-type: text/plain" -d@- 'http://localhost:3000/builds'

#### DIY

You can also roll your own. For every build `POST` JSON data to `http://localhost:3000/builds/`,
for example:

```js
{
  "jobName": "my job",
  "buildId": "42"
  "start": 1451449853542,
  "end": 1451449870555,
  "outcome": "pass",
  "inputs": [{
    "revision": "1eadcdd4d35f9a",
    "sourceId": "git@github.com:cburgmer/buildviz.git"
  }],
  "triggeredBy": [{
    "jobName": "Test",
    "buildId": "42"
  }],
  "testResults": [{
    "name": "Test Suite",
    "children": [{
      "classname": "some.class",
      "name": "A Test",
      "runtime": 2,
      "status": "pass"
    }]
  }]
}
```

The build's `jobName`, `buildId`, and `start` are required, all other values are
optional. Buildviz follows [this JSON schema](./resources/schema.json).

JUnit XML ([or JSON](https://github.com/cburgmer/buildviz/wiki#help-my-tests-dont-generate-junit-xml))
formatted test results can be `PUT` to `http://localhost:3000/builds/$JOB_NAME/$BUILD_ID/testresults`

## More

[FAQ](https://github.com/cburgmer/buildviz/wiki)

You might also like:

* [Polaris](https://sites.google.com/thoughtworks.com/polaris/home), automated tracking of engineering excellence fitness metrics.
* [Metrik](https://github.com/thoughtworks/metrik), calculates the [four key metrics](https://www.thoughtworks.com/radar/techniques/four-key-metrics) based on CI/CD build data.
* [Four Keys](https://github.com/GoogleCloudPlatform/fourkeys), measures the four key metrics.
* [HeartBeat](https://github.com/thoughtworks/HeartBeat), calculates delivery metrics from CI/CD build data, revision control and project planning tools.
* [Kuona project for IT Analytics](https://github.com/kuona/kuona-project), provides a dashboard on data from various sources.
* [Test Trend Analyzer](https://github.com/anandbagmar/tta), consumes test results for test trends.
* [TRT](https://github.com/thetestpeople/trt), consumes test results for test trends.
* [GoCD's analytics extension](https://extensions-docs.gocd.org/analytics/current/), collects and displays build metrics for GoCD.
* [BuildPulse](https://github.com/marketplace/buildpulse), automatically detects flaky tests.

Reach out to [@cburgmer](https://twitter.com/cburgmer) for feedback and ideas.
