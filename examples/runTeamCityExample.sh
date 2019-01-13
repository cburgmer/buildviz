#!/bin/bash

cat <<EOF
Appologies, but you are on your own.

TeamCity has a manual setup process, amongst that a license agreement you need to accept.

Here's what you can do:

1. $ examples/teamcity/run.sh start
2. Now go to http://localhost:8111 and click through, accepting the license (avoid altering any settings on the way), choose admin:admin as credentials.
3. Authorise the active build agent via the "Agents" tab.
4. Then switch to http://localhost:8111/overview.html and start some builds.
5. In one tab: $ ./lein do deps, ring server-headless
6. In another: $ ./lein run -m buildviz.teamcity.sync http://admin:admin@localhost:8111 -p SimpleSetup
7. Open http://localhost:3000/index.html
EOF
