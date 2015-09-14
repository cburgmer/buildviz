(function () {
    "use strict";

    var statusHeader = d3.select("body")
            .append('header')
            .attr('class', 'status');
    statusHeader
        .append("h1")
        .append('a')
        .attr('href', 'https://github.com/cburgmer/buildviz')
        .text('buildviz');

    d3.json("/status", function (_, status) {
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
