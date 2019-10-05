#cloud-config
package_update: true
package_upgrade: true
packages:
 - docker
write_files:
 - content: |
        curl -L https://github.com/docker/compose/releases/download/1.21.2/docker-compose-$(uname -s)-$(uname -m) -o /usr/local/bin/docker-compose
        chmod +x /usr/local/bin/docker-compose
        service docker start
        sudo usermod -a -G docker ec2-user
        chown ec2-user:ec2-user /home/ec2-user/
   path: /tmp/install_docker.sh
 - content: |
        cd /home/ec2-user/
        mkdir buildviz nginx # Work around https://github.com/docker/compose/issues/3391
        /usr/local/bin/docker-compose pull
        mkdir data # create mount volume ourselves, so it has the correct owner, might be https://github.com/docker/compose/issues/3270
        /usr/local/bin/docker-compose -f docker-compose.yml -f docker-compose.prod.yml up --no-build -d
   path: /tmp/run.sh
 - encoding: b64
   content: ${base64encode(docker_compose_file)}
   path: /home/ec2-user/docker-compose.yml
 - encoding: b64
   content: ${base64encode(docker_compose_prod_file)}
   path: /home/ec2-user/docker-compose.prod.yml
runcmd:
 - 'bash /tmp/install_docker.sh'
 - 'sudo -u ec2-user bash /tmp/run.sh'
