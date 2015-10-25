(function (timespanSelection, graphFactory, utils, jobColors, dataSource) {
    var buildEntries = function (pipeline) {
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

    var buildHierarchy = function (jobEntries) {
        var hierarchy = d3.nest()
                .key(function (d) {
                    return d.name.split(' ')[0];
                })
                .entries(jobEntries);
        return hierarchy.map(function (group) {
            return {
                name: group.key,
                children: group.values
            };
        });
    };

    var treemap = d3.layout.treemap()
            .size([graphFactory.size, graphFactory.size])
            .sort(function (a, b) {
                return a.value - b.value;
            });

    var skipParentNodes = function (nodes) {
        return nodes.filter(function(d) { return d.depth > 0 && !d.children; });
    };

    var renderGraph = function (root, svg) {
        var jobNames = Object.keys(root),
            color = jobColors.colors(jobNames),
            builds = buildHierarchy(buildEntries(root));

        var selection = svg
                .selectAll("g")
                .data(skipParentNodes(treemap.nodes({children: builds})),
                      function (d) {
                          return d.name;
                      });

        selection.exit()
            .remove();

        var node = selection
                .enter()
                .append("g")
                .attr('data-id', function (d) {
                    return 'jobname-' + d.name;
                });

        node.append('rect')
            .style('fill', function (d) {
                return color(d.name);
            });

        node.append("title");

        node.append("text")
            .style("text-anchor", "middle");

        selection
            .transition()
            .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });

        selection.select('rect')
            .transition()
            .attr('width', function (d) {
                return d.dx;
            })
            .attr('height', function (d) {
                return d.dy;
            });

        selection.select('title')
            .text(function(d) { return d.title; });

        selection.select('text')
            .selectAll('*')
            .remove();
        selection.select('text')
            .transition()
            .attr("transform", function(d) { return "translate(" + (d.dx / 2) + "," + (d.dy / 2) + ")"; })
            .each(function (d) {
                if (d.dx > 90 && d.dy > 50) {
                    graphFactory.textWithLineBreaks(this, d.name.split(' '));
                }
            });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoWeeks),
        graph = graphFactory.create({
            id: 'averageJobRuntime',
            headline: "Average job runtime",
            description: "<h3>Where is most of the time spent?</h3><i>Size: average runtime, color: job (similar colors for job group)</i>",
            csvUrl: "/jobs.csv",
            noDataReason: "provided <code>start</code> and <code>end</code> times for your builds",
            widgets: [timespanSelector.widget]
        });

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.load('/jobs?from=' + fromTimestamp, function (data) {
            graph.loaded();

            renderGraph(data, graph.svg);
        });
    });

}(timespanSelection, graphFactory, utils, jobColors, dataSource));
