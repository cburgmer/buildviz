(function (widget) {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var diameter = 600,
        className = "failedBuilds";

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
                    title: jobName + ': ' + failedCount + ' (' + (ratio * 100) + '%)',
                    failRatio: ratio,
                    value: failedCount
                };
            });
    };

    var maxFailRatio = function (pipeline) {
        var failRatios = Object.keys(pipeline)
            .map(function (jobName) {
                return failRatio(pipeline[jobName]);
            });

        if (failRatios.length > 0) {
            return Math.max.apply(null, failRatios);
        } else {
            // Something valid
            return 1;
        }
    };

    var svg = widget.create("Failed Builds", "Color: Failure Ratio, Diameter: Number of Failures")
            .svg(diameter)
            .attr("class", className);

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

    d3.json('/pipeline', function (_, root) {
        var failRatio = maxFailRatio(root),
            color = colorScale(failRatio);

        var node = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: failedBuildsAsBubbles(root)})))
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
}(widget));
