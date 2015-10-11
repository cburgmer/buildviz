(function (widget, dataSource) {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var diameter = 600,
        jobCount = 5,
        worstFlakyRatio = 0.10;

    var flakyRatio = function (job) {
        var flakyCount = job.flakyCount || 0;
        return flakyCount / job.totalCount;
    };

    var flakyBuildsAsBubbles = function (pipeline) {
        return Object.keys(pipeline)
            .filter(function (jobName) {
                return pipeline[jobName].flakyCount > 0;
            })
            .map(function (jobName) {
                var flakyCount = pipeline[jobName].flakyCount,
                    ratio = flakyRatio(pipeline[jobName]);
                return {
                    name: jobName,
                    title: jobName + '\n\n' + flakyCount + ' flaky failures\n' + (ratio * 100).toFixed(0) + '% of the time',
                    flakyRatio: ratio,
                    value: flakyCount
                };
            });
    };

    var selectMostFlaky = function (pipeline, n) {
        var jobNames = Object.keys(pipeline);
        jobNames.sort(function (jobA, jobB) {
            return pipeline[jobA].flakyCount - pipeline[jobB].flakyCount;
        });

        var selectedPipeline = {};
        jobNames.slice(-n).forEach(function (job) {
            selectedPipeline[job] = pipeline[job];
        });
        return selectedPipeline;
    };

    var svg = widget.create("Top " + jobCount + " flaky builds",
                            "Color: Flaky Ratio, Diameter: Flaky Count",
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

    var color = colorScale(worstFlakyRatio);

    var timestampTwoWeeksAgo = function () {
        var today = new Date(),
            twoWeeksAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 14);
        return +twoWeeksAgo;
    };

    dataSource.load('/jobs?from=' + timestampTwoWeeksAgo(), function (root) {
        if (!Object.keys(root).length) {
            return;
        }

        var selectedData = selectMostFlaky(root, jobCount);

        var node = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: flakyBuildsAsBubbles(selectedData)})))
                .enter()
                .append("g")
                .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });;

        node.append("title")
            .text(function(d) { return d.title; });

        node.append("circle")
            .attr("r", function (d) { return d.r; })
            .style("fill", function(d) { return color(d.flakyRatio); });

        node.append("text")
            .style("text-anchor", "middle")
            .each(function (d) {
                widget.textWithLineBreaks(this, d.name.split(' '));
            });
    });
}(widget, dataSource));
