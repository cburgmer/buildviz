#!/bin/bash
# With ideas from https://github.com/rgl/jenkins-vagrant/blob/master/provision.sh
set -eo pipefail

wget -q -O - https://jenkins-ci.org/debian/jenkins-ci.org.key | sudo apt-key add -
sudo sh -c 'echo deb http://pkg.jenkins-ci.org/debian binary/ > /etc/apt/sources.list.d/jenkins.list'
sudo apt-get update
sudo apt-get -qy install default-jre
sudo apt-get -qy install curl git

sudo apt-get -qy install jenkins xmlstarlet

# Install dependencies and their dependencies, don't know how to have Jenkins resolve that for us :(
for PLUGIN in git scm-api token-macro promoted-builds git-client parameterized-trigger build-pipeline-plugin jquery dashboard-view workflow-scm-step credentials conditional-buildstep matrix-project script-security workflow-job workflow-step-api workflow-api workflow-support ssh-credentials structs maven-plugin javadoc junit mailer display-url-api run-condition; do
    curl --silent --location http://updates.jenkins-ci.org/latest/${PLUGIN}.hpi -o /var/lib/jenkins/plugins/${PLUGIN}.hpi > /dev/null
    chown jenkins:jenkins /var/lib/jenkins/plugins/${PLUGIN}.hpi
done

while [ ! -s /var/lib/jenkins/secrets/initialAdminPassword ]; do sleep 1; done

sudo service jenkins stop

# disable security, see https://jenkins.io/doc/book/operating/security/#disabling-security
xmlstarlet edit --inplace -u '/hudson/useSecurity' -v 'false' /var/lib/jenkins/config.xml
xmlstarlet edit --inplace -d '/hudson/authorizationStrategy' /var/lib/jenkins/config.xml
xmlstarlet edit --inplace -d '/hudson/securityRealm' /var/lib/jenkins/config.xml

# disable showing the wizard on the first access.
cp -p /var/lib/jenkins/jenkins.install.UpgradeWizard.state /var/lib/jenkins/jenkins.install.InstallUtil.lastExecVersion

sudo service jenkins start

echo "Waiting for Jenkins to come up"
until $(curl --output /dev/null --silent --head --fail http://localhost:8080); do
    sleep 1
done

CRUMB=$(curl -s 'http://localhost:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)')

# Create or Update
pushd .
cd /mnt/jobs
for JOB_CONFIG in *; do
    JOB_NAME=$( echo $JOB_CONFIG | sed s/.xml$// )
    curl --silent -X POST -H "$CRUMB" --data-binary "@/mnt/jobs/$JOB_CONFIG" -H "Content-Type: application/xml" "http://localhost:8080/createItem?name=$JOB_NAME" > /dev/null
    curl --silent -X POST -H "$CRUMB" --data-binary "@/mnt/jobs/$JOB_CONFIG" "http://localhost:8080/job/$JOB_NAME/config.xml" > /dev/null
done
cd /mnt/views
for VIEW_CONFIG in *; do
    VIEW_NAME=$( echo $VIEW_CONFIG | sed s/.xml$// )
    curl --silent -X POST -H "$CRUMB" --data-binary "@/mnt/views/$VIEW_CONFIG" -H "Content-Type: application/xml" "http://localhost:8080/createView?name=$VIEW_NAME" > /dev/null
    curl --silent -X POST -H "$CRUMB" --data-binary "@/mnt/views/$VIEW_CONFIG" "http://localhost:8080/view/$VIEW_NAME/config.xml" > /dev/null
done
popd

echo "Triggering builds"
for I in 1 2 3 4 5; do
    curl --silent -X POST -H "$CRUMB" http://localhost:8080/job/Test/build > /dev/null
    # Wait for build to run to enqueue next
    sleep 10
done
