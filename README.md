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

## Example

Here's [buildviz's look at ci.jenkins-ci.org](http://cburgmer.github.io/buildviz/ci.jenkins-ci.org/), the Jenkins project's own build server instance.

## Usage

    $ curl -OL https://github.com/cburgmer/buildviz/releases/download/0.10.0/buildviz-0.10.0-standalone.jar
    $ java -jar buildviz-0.10.0-standalone.jar

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

E.g. sync existing history from Go.cd (see `--help` for details):

    $ java -cp buildviz-0.10.0-standalone.jar buildviz.go.sync http://$USER:$PW@localhost:8153/go

There's support for [Jenkins, Go.cd and TeamCity](https://github.com/cburgmer/buildviz/wiki/CI-tool-integration).

## More

[FAQ](https://github.com/cburgmer/buildviz/wiki)

You might also like [Kuona - Delivery Dashboard Generator](https://github.com/kuona/kuona), [Test Trend Analyzer](https://github.com/anandbagmar/tta) or [TRT](https://github.com/thetestpeople/trt).

Reach out to [@cburgmer](https://twitter.com/cburgmer) for feedback and ideas.
