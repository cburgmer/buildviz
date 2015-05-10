(function () {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var diameter = 600,
        className = "failedBuilds";

    var failedBuildsAsBubbles = function (pipeline) {
        return Object.keys(pipeline)
            .filter(function (jobName) {
                return pipeline[jobName].failedCount > 0;
            })
            .map(function (jobName) {
            var failedCount = pipeline[jobName].failedCount;
            return {
                name: jobName,
                title: jobName + ': ' + failedCount,
                value: failedCount
            };
        });
    };

    d3.select("body").append("h1")
        .text("Failed Builds");
    var svg = d3.select("body").append("svg")
            .attr("width", diameter)
            .attr("height", diameter)
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
                .data(noGrouping(bubble.nodes({children: failedBuildsAsBubbles(root)})))
                .enter()
                .append("g")
                .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });;

        node.append("title")
            .text(function(d) { return d.title; });

        node.append("circle")
            .attr("r", function (d) { return d.r; })
            .style("fill", function(d) { return color(d.name); });

        node.append("text")
            .attr("dy", ".3em")
            .style("text-anchor", "middle")
            .text(function(d) { return d.name; });
    });
}());
