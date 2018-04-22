#!/bin/bash
# With ideas from https://github.com/rgl/jenkins-vagrant/blob/master/provision.sh
set -eo pipefail

jenkins_queue_length() {
    curl --silent http://localhost:8080/queue/api/json | python -c "import json; import sys; print len(json.loads(sys.stdin.read())['items'])"
}

wget -q -O - https://jenkins-ci.org/debian/jenkins-ci.org.key | sudo apt-key add -
sudo sh -c 'echo deb http://pkg.jenkins-ci.org/debian binary/ > /etc/apt/sources.list.d/jenkins.list'
sudo apt-get update
sudo apt-get -qy install default-jre curl git python
sudo apt-get -qy install jenkins # only install after all dependencies are already available

echo "Disabling Jenkins security"
while [ ! -s /var/lib/jenkins/secrets/initialAdminPassword ]; do sleep 1; done
sudo service jenkins stop

# disable security, see https://jenkins.io/doc/book/operating/security/#disabling-security
sleep 10
sudo rm /var/lib/jenkins/config.xml

# disable showing the wizard on the first access.
cp -p /var/lib/jenkins/jenkins.install.UpgradeWizard.state /var/lib/jenkins/jenkins.install.InstallUtil.lastExecVersion

sudo service jenkins start

echo "Waiting for Jenkins to come up"
until curl --output /dev/null --silent --head --fail http://localhost:8080; do sleep 1; done

echo "Installing plugins"
# Need to fetch plugin list first, Jenkins fails with 'No update center data is retrieved yet from: https://updates.jenkins.io/update-center.json' if we don't do this manually
curl --silent -X POST http://localhost:8080/pluginManager/checkUpdatesServer > /dev/null

for PLUGIN in git promoted-builds git-client parameterized-trigger build-pipeline-plugin dashboard-view; do
    sudo java -jar /var/cache/jenkins/war/WEB-INF/jenkins-cli.jar -s http://localhost:8080/ install-plugin "${PLUGIN}"
done

sudo service jenkins restart

echo "Waiting for Jenkins to come up"
until curl --output /dev/null --silent --head --fail http://localhost:8080; do sleep 1; done

# Create or Update
echo "Configuring pipeline"
pushd .
cd /mnt/jobs
for JOB_CONFIG in *; do
    # shellcheck disable=SC2001
    JOB_NAME=$( echo "$JOB_CONFIG" | sed s/.xml$// )
    curl --silent -X POST --data-binary "@/mnt/jobs/$JOB_CONFIG" -H "Content-Type: application/xml" "http://localhost:8080/createItem?name=$JOB_NAME" > /dev/null
    curl --silent -X POST --data-binary "@/mnt/jobs/$JOB_CONFIG" "http://localhost:8080/job/$JOB_NAME/config.xml" > /dev/null
done
cd /mnt/views
for VIEW_CONFIG in *; do
    # shellcheck disable=SC2001
    VIEW_NAME=$( echo "$VIEW_CONFIG" | sed s/.xml$// )
    curl --silent -X POST --data-binary "@/mnt/views/$VIEW_CONFIG" -H "Content-Type: application/xml" "http://localhost:8080/createView?name=$VIEW_NAME" > /dev/null
    curl --silent -X POST --data-binary "@/mnt/views/$VIEW_CONFIG" "http://localhost:8080/view/$VIEW_NAME/config.xml" > /dev/null
done
popd

echo "Triggering builds"
for _ in 1 2 3 4 5; do
    curl --silent -X POST http://localhost:8080/job/Test/build > /dev/null

    # Wait for build to run to enqueue next
    sleep 2
    until [[ "$(jenkins_queue_length)" -eq 0 ]] ; do
        sleep 1
    done
done
