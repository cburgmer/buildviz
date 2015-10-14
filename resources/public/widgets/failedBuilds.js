(function (widget, dataSource) {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var diameter = 600,
        jobCount = 5,
        worstFailureRatio = 0.25;

    var failRatio = function (job) {
        var failCount = job.failedCount || 0;
        return failCount / job.totalCount;
    };

    var failedBuildsAsBubbles = function (pipeline) {
        return Object.keys(pipeline)
            .filter(function (jobName) {
                return pipeline[jobName].failedCount > 0;
            })
            .map(function (jobName) {
                var failedCount = pipeline[jobName].failedCount,
                    ratio = failRatio(pipeline[jobName]);
                return {
                    name: jobName,
                    title: jobName + '\n\n' + failedCount + ' failures\n' + (ratio * 100).toFixed(0) + '% of the time',
                    failRatio: ratio,
                    value: failedCount
                };
            });
    };

    var selectMostFailed = function (pipeline, n) {
        var jobNames = Object.keys(pipeline);
        jobNames.sort(function (jobA, jobB) {
            return pipeline[jobA].failedCount - pipeline[jobB].failedCount;
        });

        var selectedPipeline = {};
        jobNames.slice(-n).forEach(function (job) {
            selectedPipeline[job] = pipeline[job];
        });
        return selectedPipeline;
    };

    var svg = widget.create("Top 5 failed builds",
                            "<h3>What needs most manual intervention? Where are the biggest quality issues? Where do we receive either not so valuable or actually very valuable feedback?</h3><i>Color: failure ratio, diameter: number of failures</i>",
                            "/jobs.csv")
            .svg(diameter);

    var bubble = d3.layout.pack()
            .sort(null)
            .size([diameter, diameter])
            .padding(1.5),
        noGrouping = function (bubbleNodes) {
            return bubbleNodes.filter(function(d) { return !d.children; });
        };

    var colorScale = function (maxDomain) {
        return d3.scale.linear()
            .domain([0, maxDomain])
            .range(["white", d3.rgb("red").darker()])
            .interpolate(d3.interpolateLab);
    };

    var color = colorScale(worstFailureRatio);

    var timestampTwoWeeksAgo = function () {
        var today = new Date(),
            twoWeeksAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 14);
        return +twoWeeksAgo;
    };

    dataSource.load('/jobs?from=' + timestampTwoWeeksAgo(), function (root) {
        if (!Object.keys(root).length) {
            return;
        }

        var selectedData = selectMostFailed(root, jobCount);

        var node = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: failedBuildsAsBubbles(selectedData)})))
                .enter()
                .append("g")
                .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });;

        node.append("title")
            .text(function(d) { return d.title; });

        node.append("circle")
            .attr("r", function (d) { return d.r; })
            .style("fill", function(d) { return color(d.failRatio); });

        node.append("text")
            .style("text-anchor", "middle")
            .each(function (d) {
                widget.textWithLineBreaks(this, d.name.split(' '));
            });
    });
}(widget, dataSource));
