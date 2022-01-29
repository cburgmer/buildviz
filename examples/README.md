## Buildviz Examples

### Hosted on AWS

There used to be a live example hosted on AWS, but it got a bit expensive over time.
The infrastructure code for that can be found in `./aws/`.

### Simple seed data

This is the quickest, most lightweight example with some fake seed data:

    $ ./runSeedDataExample.sh

### Integration with build servers

[build-facts](https://github.com/cburgmer/build-facts) hosts
[examples for all supported build servers](https://github.com/cburgmer/build-facts/tree/master/examples).

Follow the steps there to run your build server of choice. Once you have that
and a running instance of buildviz, run build-facts and pipe the extracted data
through curl.

Here is an example for the Jenkins setup:

    $ java -jar build-facts-0.5.4-standalone.jar jenkins http://localhost:8080 --state state.json \
        | curl -v -H "Content-type: text/plain" -d@- 'http://localhost:3000/builds'
