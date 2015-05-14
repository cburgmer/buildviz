# buildviz

Transparency for your build pipeline's results and runtime.

**This is experimental and things are going to move around a lot**

## Usage

    $ ./lein ring server-headless
    # from a 2nd tab
    $ ./seedDummyData.sh
    $ open http://localhost:3000/index.html

## Run as standalone jar

    $ ./lein ring uberjar
    $ java -jar target/buildviz-*-standalone.jar

## Full fledged exampe

Install Go.cd

    $ vagrant init gocd/gocd-demo
    $ vagrant up

Start buildviz

    $ ./lein ring server-headless

Sync buildviz with the Go builds

    $ ./lein exec scripts/gosync.clj http://localhost:8153/go
