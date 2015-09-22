(function (widget, dataSource) {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var diameter = 600;

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
                    title: jobName + ': ' + flakyCount + ' (' + (ratio * 100) + '%)',
                    flakyRatio: ratio,
                    value: flakyCount
                };
            });
    };

    var maxFlakyRatio = function (pipeline) {
        var flakyRatios = Object.keys(pipeline)
            .map(function (jobName) {
                return flakyRatio(pipeline[jobName]);
            });

        if (flakyRatios.length > 0) {
            return Math.max.apply(null, flakyRatios);
        } else {
            // Something valid
            return 1;
        }
    };

    var svg = widget.create("Flaky builds",
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

    dataSource.load('/jobs', function (root) {
        if (!Object.keys(root).length) {
            return;
        }

        var flakyRatio = maxFlakyRatio(root),
            color = colorScale(flakyRatio);

        var node = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: flakyBuildsAsBubbles(root)})))
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
