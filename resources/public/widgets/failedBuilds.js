(function (widget, dataSource, jobColors) {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var diameter = 600,
        jobCount = 5,
        borderWidthInPx = 30,
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

    var widgetInstance = widget.create("Top 5 failed builds",
                                       "<h3>What needs most manual intervention? Where are the biggest quality issues? Where do we receive either not so valuable or actually very valuable feedback?</h3><i>Border color: failure ratio, inner color: job, diameter: number of failures</i>",
                                       "/jobs.csv",
                                      "provided the <code>outcome</code> of your builds");
    var svg = widgetInstance
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
        widgetInstance.loaded();

        var jobNames = Object.keys(root),
            jobColor = jobColors.colors(jobNames),
            failedBuilds = failedBuildsAsBubbles(selectMostFailed(root, jobCount));

        if (!failedBuilds.length) {
            return;
        }

        var node = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: failedBuilds})))
                .enter()
                .append("g")
                .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });;

        node.append("title")
            .text(function(d) { return d.title; });

        node.append("circle")
            .attr("r", function (d) { return (d.r - borderWidthInPx / 2); })
            .attr("stroke-width", borderWidthInPx)
            .style("fill", function (d) {
                return jobColor(d.name);
            })
            .style("stroke", function(d) { return color(d.failRatio); });

        node.append("text")
            .style("text-anchor", "middle")
            .each(function (d) {
                widget.textWithLineBreaks(this, d.name.split(' '));
            });
    });
}(widget, dataSource, jobColors));
