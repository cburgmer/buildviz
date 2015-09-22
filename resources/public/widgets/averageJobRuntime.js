(function (widget, utils, dataSource) {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var diameter = 600;

    var buildRuntimeAsBubbles = function (pipeline) {
        return Object.keys(pipeline)
            .filter(function (jobName) {
                return pipeline[jobName].averageRuntime;
            })
            .map(function (jobName) {
                var averageRuntime = pipeline[jobName].averageRuntime,
                    runtime = averageRuntime ? ' (' + utils.formatTimeInMs(averageRuntime) + ')' : '';

                return {
                    name: jobName,
                    title: jobName + ' ' + runtime,
                    value: averageRuntime
                };
            });
    };

    var svg = widget.create("Average job runtime",
                            "Color: jobGroup, Diameter: avg. Runtime",
                            "/jobs.csv")
            .svg(diameter);

    var bubble = d3.layout.pack()
            .sort(null)
            .size([diameter, diameter])
            .padding(1.5),
        noGrouping = function (bubbleNodes) {
            return bubbleNodes.filter(function(d) { return !d.children; });
        };

    var color = d3.scale.category20c();

    dataSource.load('/jobs', function (root) {
        if (!Object.keys(root).length) {
            return;
        }

        var node = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: buildRuntimeAsBubbles(root)})))
                .enter()
                .append("g")
                .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });;

        node.append("title")
            .text(function(d) { return d.title; });

        node.append("circle")
            .attr("r", function (d) { return d.r; })
            .style("fill", function(d) {
                var jobGroup = d.name.split(' ')[0];
                return color(jobGroup);
            });

        node.append("text")
            .style("text-anchor", "middle")
            .each(function (d) {
                widget.textWithLineBreaks(this, d.name.split(' '));
            });
    });
}(widget, utils, dataSource));
