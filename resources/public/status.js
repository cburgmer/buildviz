(function (dataSource) {
    "use strict";

    const statusHeader = d3.select("body")
            .append('header')
            .attr('class', 'status');
    const h1 = statusHeader
            .append("h1");

    const pipelineNameSpan = h1.append('span');
    h1.append('a')
        .attr('href', 'https://github.com/cburgmer/buildviz')
        .text('buildviz');

    const baseUrl = function () {
        return window.location.href.replace(new RegExp('/[^/]*([\\?#].*)?$'), '');
    };

    const showInitialHelp = function (header) {
        const now = +new Date(),
            aMinuteAgo = now - 61234,
            serverUrl = baseUrl(),
            commands = ['echo \'{"start":' + aMinuteAgo + ',"end":' + now + ',"outcome":"fail"}\' \\\n  | curl -X PUT -H "Content-type: application/json" -d@- ' + serverUrl + '/builds/my_job/42',
                        'echo \'<testsuite name="my_suite"><testcase classname="my_class" name="my_test" time="0.42"><failure/></testcase></testsuite>\' \\\n  | curl -X PUT -H "Content-type: text/xml" -d@- ' + serverUrl + '/builds/my_job/42/testresults'];

        header.append('p')
            .attr('class', 'help')
            .text('Want to get a feel for buildviz? Start filling in some data:')
            .append('pre')
            .text(commands.join('\n'));
    };

    const durationToString = function (start, end) {
        const duration = end - start,
            momentDuration = moment.duration(duration);
        return momentDuration.format("w [weeks], d [days], h [hours], m [minutes]", {
            largest: 1,
            usePlural: false
        });
    };

    dataSource.load("status", function (status) {
        if (status.pipelineName) {
            pipelineNameSpan.text(status.pipelineName + ' - ');

            document.title = status.pipelineName + ' - ' + document.title;
        }
        statusHeader
            .append('span')
            .attr('class', 'details')
            .text(function () {
                const latestBuild = status.latestBuildStart ?
                        ', latest from ' + durationToString(status.latestBuildStart, +Date.now()) + ' ago'
                        : '',
                    totalTimeOfBuilds = status.earliestBuildStart ?
                        ', ' + durationToString(status.earliestBuildStart, status.latestBuildStart) + "' worth of data"
                        : '';
                return " (" + status.totalBuildCount.toLocaleString() + " builds" + totalTimeOfBuilds + latestBuild + ")";
            });

        if (status.totalBuildCount === 0) {
            showInitialHelp(statusHeader);
        }
    });
})(dataSource);
