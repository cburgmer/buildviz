(function (widget) {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var diameter = 600;

    var padZero = function (value) {
        if (value < 10) {
            return '0' + value;
        } else {
            return '' + value;
        }
    };

    var formatTimeInMs = function (timeInMs) {
        var hours = Math.floor(timeInMs / (60 * 60 * 1000)),
            minutes = Math.floor(timeInMs % (60 * 60 * 1000) / (60 * 1000)),
            seconds = Math.round(timeInMs % (60 * 1000) / 1000);

        return hours + ':' + padZero(minutes) + ':' + padZero(seconds);
    };

    var buildRuntimeAsBubbles = function (pipeline) {
        return Object.keys(pipeline)
            .filter(function (jobName) {
                return pipeline[jobName].averageRuntime;
            })
            .map(function (jobName) {
                var averageRuntime = pipeline[jobName].averageRuntime,
                    runtime = averageRuntime ? ' (' + formatTimeInMs(averageRuntime) + ')' : '';

                return {
                    name: jobName,
                    title: jobName + ': ' + runtime,
                    value: averageRuntime
                };
            });
    };

    var svg = widget.create("Average Job Build Time","Color: jobGroup, Diameter: avg. Runtime")
            .svg(diameter);

    var bubble = d3.layout.pack()
            .sort(null)
            .size([diameter, diameter])
            .padding(1.5),
        noGrouping = function (bubbleNodes) {
            return bubbleNodes.filter(function(d) { return !d.children; });
        };

    var color = d3.scale.category20c();

    d3.json('/jobs', function (_, root) {
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
