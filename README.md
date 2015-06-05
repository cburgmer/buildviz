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
    # from a 2nd tab
    $ ./seedDummyData.sh
    $ open http://localhost:3000/index.html

## Run as standalone jar

    $ ./lein ring uberjar
    $ java -jar target/buildviz-*-standalone.jar

## Full fledged example

Install Go.cd

    $ vagrant init gocd/gocd-demo; vagrant box update; vagrant up

Start buildviz

    $ ./lein ring server-headless

Sync buildviz with the Go builds

    $ ./lein exec scripts/gosync.clj http://localhost:8153/go
