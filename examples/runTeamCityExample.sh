#!/bin/bash

cat <<EOF
Appologies, but you are on your own.

TeamCity has a manual setup process, amongst that a license agreement you need to accept.

Here's what you can do:

1. $ ./teamcity/run.sh start
2. Please follow instructions there
3. Then switch to http://localhost:8111/overview.html and start some builds.
4. In one tab: $ ./lein do deps, ring server-headless
5. In another: $ ./lein run -m buildviz.teamcity.sync http://admin:admin@localhost:8111 -p SimpleSetup
6. Open http://localhost:3000/index.html
EOF
