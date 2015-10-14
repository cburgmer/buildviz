(function (widget, utils, jobColors, dataSource) {
    // Roughly following http://bl.ocks.org/mbostock/4063269
    var diameter = 600;

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

    var svg = widget.create("Average job runtime",
                            "<h3>Where is most of the time spent?</h3><i>Size: average runtime, color: job (similar colors for job group)</i>",
                            "/jobs.csv")
            .svg(diameter)
            .attr('class', 'averageJobRuntime');

    var treemap = d3.layout.treemap()
            .size([diameter, diameter])
            .sort(function (a, b) {
                return a.value - b.value;
            });

    var timestampTwoWeeksAgo = function () {
        var today = new Date(),
            twoWeeksAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 14);
        return +twoWeeksAgo;
    };

    dataSource.load('/jobs?from=' + timestampTwoWeeksAgo(), function (root) {
        var jobNames = Object.keys(root),
            color = jobColors.colors(jobNames),
            builds = buildHierarchy(buildEntries(root));

        if (!builds.length) {
            return;
        }

        var node = svg
                .datum({children: builds})
                .selectAll("g")
                .data(treemap.nodes)
                .enter()
                .append("g")
                .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; })
                .attr('data-id', function (d) {
                    if (d.name) {
                        return 'jobname-' + d.name;
                    }
                });

        node.append('rect')
            .attr('width', function (d) {
                return d.dx;
            })
            .attr('height', function (d) {
                return d.dy;
            })
            .style('fill', function (d) {
                if (d.name) {
                    return color(d.name);
                }
                return 'transparent';
            });

        node.append("title")
            .text(function(d) { return d.title; });

        node.append("text")
            .attr("transform", function(d) { return "translate(" + (d.dx / 2) + "," + (d.dy / 2) + ")"; })
            .style("text-anchor", "middle")
            .each(function (d) {
                if (!d.children && d.name && d.dx > 90 && d.dy > 50) {
                    widget.textWithLineBreaks(this, d.name.split(' '));
                }
            });
    });
}(widget, utils, jobColors, dataSource));
