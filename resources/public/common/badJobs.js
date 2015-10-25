var badJobs = function (jobColors) {
    "use strict";

    var module = {};

    var borderWidthInPx = 30;

    var selectWorst = function (pipeline, n) {
        pipeline.sort(function (jobA, jobB) {
            return jobA.value - jobB.value;
        });

        return pipeline.slice(-n);
    };

    var bubble = d3.layout.pack()
            .sort(null)
            .size([graphFactory.size, graphFactory.size])
            .padding(1.5),
        noGrouping = function (bubbleNodes) {
            return bubbleNodes.filter(function(d) { return d.depth > 0; });
        };

    var colorScale = function (maxDomain) {
        return d3.scale.linear()
            .domain([0, maxDomain])
            .range(["white", d3.rgb("red").darker()])
            .interpolate(d3.interpolateLab);
    };

    module.renderData = function (data, svg, jobCount, worstFailureRatio) {
        var jobNames = data.map(function (job) {
            return job.name;
        });
        var jobColor = jobColors.colors(jobNames),
            worstJobs = selectWorst(data, jobCount);

        var color = colorScale(worstFailureRatio);

        var selection = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: worstJobs})),
                      function(d) { return d.name; });

        selection
            .exit()
            .remove();

        var node = selection
                .enter()
                .append('g');

        node.append('title');
        node.append('circle')
            .attr("stroke-width", borderWidthInPx)
            .style("fill", function (d) {
                return jobColor(d.name);
            });
        node.append('text')
            .style("text-anchor", "middle")
            .each(function (d) {
                graphFactory.textWithLineBreaks(this, d.name.split(' '));
            });

        selection
            .transition()
            .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });

        selection.select('title')
            .text(function(d) { return d.title; });

        selection.select('circle')
            .transition()
            .attr("r", function (d) { return (d.r - borderWidthInPx / 2); })
            .style("stroke", function(d) { return color(d.ratio); });
    };

    return module;
}(jobColors);
