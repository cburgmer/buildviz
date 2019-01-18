## Buildviz Examples

### Free (as in lunch) example

http://buildviz.cburgmer.space/ is a live example tracking KotlinTools from
https://teamcity.jetbrains.com.

The example is hosted on AWS and the infrastructure code for that can be found
in `./aws/`.

### Simple seed data

This is the quickest, most lightweight example with some fake seed data:

    $ ./runSeedDataExample.sh

### Integration with CI servers

These demos show integration with popular CI servers. Installed via Docker, they
come with a minimal pipeline pre-configured.

    $ ./runGoCdExample.sh       # Go.cd
    $ ./runJenkinsExample.sh    # Jenkins
    $ ./runTeamCityExample.sh   # TeamCity

Re-running the examples will re-use the provisioned containers.

#### Cleaning up

To remove the test instance and then the downloaded docker images run

    $ ./jenkins/run.sh destroy
    $ ./jenkins/run.sh purge
