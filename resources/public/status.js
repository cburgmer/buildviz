(function () {
    "use strict";

    var statusHeader = d3.select("body")
            .append('header')
            .attr('class', 'status');
    var h1 = statusHeader
            .append("h1");

    var pipelineNameSpan = h1.append('span');
    h1.append('a')
        .attr('href', 'https://github.com/cburgmer/buildviz')
        .text('buildviz');

    d3.json("/status", function (_, status) {
        if (status.pipelineName) {
            pipelineNameSpan.text(status.pipelineName + ' - ');

            document.title = status.pipelineName + ' - ' + document.title;
        }
        statusHeader
            .append('span')
            .attr('class', 'details')
            .text(function () {
                var latestBuild = status.latestBuildStart ?
                        ', latest from ' + new Date(status.latestBuildStart).toLocaleString()
                        : '';
                return " (" + status.totalBuildCount + " builds" + latestBuild + ")";
            });
    });
})();
