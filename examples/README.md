## Buildviz Examples

### Hosted on AWS 

There used to be a live example hosted on AWS, but it got a bit expensive over time.
The infrastructure code for that can be found in `./aws/`.

### Simple seed data

This is the quickest, most lightweight example with some fake seed data:

    $ ./runSeedDataExample.sh

### Integration with CI servers

These demos show integration with popular CI servers. Installed via Docker, they
come with a minimal pipeline pre-configured.

    $ ./runGoCdExample.sh       # GoCD
    $ ./runJenkinsExample.sh    # Jenkins
    $ ./runTeamCityExample.sh   # TeamCity

Re-running the examples will re-use the provisioned containers.

#### Cleaning up

To remove the test instance and then the downloaded docker images run, e.g.

    $ ./jenkins/run.sh destroy
    $ ./jenkins/run.sh purge
