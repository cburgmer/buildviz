(function (widget) {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var diameter = 600,
        className = "averageJobBuildTime";

    var buildRuntimeAsBubbles = function (pipeline) {
        return Object.keys(pipeline)
            .filter(function (jobName) {
                return pipeline[jobName].averageRuntime;
            })
            .map(function (jobName) {
                var avgRuntime = pipeline[jobName].averageRuntime;
                return {
                    name: jobName,
                    title: jobName + ': ' + avgRuntime,
                    value: avgRuntime
                };
            });
    };

    var svg = widget.create("Average Job Build Time","Color: jobGroup, Diameter: avg. Runtime")
            .svg(diameter)
            .attr("class", className);

    var bubble = d3.layout.pack()
            .sort(null)
            .size([diameter, diameter])
            .padding(1.5),
        noGrouping = function (bubbleNodes) {
            return bubbleNodes.filter(function(d) { return !d.children; });
        };

    var color = d3.scale.category20c();

    d3.json('/pipeline', function (_, root) {
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
}(widget));
