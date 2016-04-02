cat <<EOF
Sorry mate,

you are in your own.

TeamCity has a manual setup process, amongst that a license agreement you need to accept.

Here's what you need to do:

1. $ vagrant up
2. Now click through and accept the license (avoid altering any settings on the way), choose admin:admin as credentials.
3. Then switch to http://localhost:8080/project.html?projectId=SimpleSetup&tab=projectOverview and start some builds.
4. In one tab: $ ./lein do deps, ring server-headless
5. In another: $ ./lein run -m buildviz.teamcity.sync http://admin:admin@localhost:8111 -p SimpleSetup
6. Open http://localhost:3000/index.html
EOF
