(function (timespanSelection, graphDescription, graphFactory, utils, jobColors, dataSource) {
    const buildEntries = function (jobEntries) {
        return jobEntries
            .filter(function (job) {
                return job.averageRuntime;
            })
            .map(function (job) {
                const averageRuntime = job.averageRuntime,
                    runtime = averageRuntime ? ' (' + utils.formatTimeInMs(averageRuntime) + ')' : '';

                return {
                    name: job.jobName,
                    title: job.jobName + ' ' + runtime,
                    value: averageRuntime
                };
            });
    };

    const buildHierarchy = function (jobEntries) {
        const hierarchy = d3.nest()
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

    const treemap = d3.layout.treemap()
            .size([graphFactory.size, graphFactory.size])
            .sort(function (a, b) {
                return a.value - b.value;
            });

    const skipParentNodes = function (nodes) {
        return nodes.filter(function(d) { return d.depth > 0 && !d.children; });
    };

    const renderGraph = function (jobEntries, svg) {
        const jobNames = jobEntries.map(function (job) {
            return job.jobName;
        });
        const color = jobColors.colors(jobNames),
            builds = buildHierarchy(buildEntries(jobEntries));

        const selection = svg
                .selectAll("g")
                .data(skipParentNodes(treemap.nodes({children: builds})),
                      function (d) {
                          return d.name;
                      });

        selection.exit()
            .remove();

        const node = selection
            .enter()
            .append("g")
            .on("mouseover", function(d) {
                window.dispatchEvent(new CustomEvent('jobSelected', {detail: {jobName: d.name}}));
            })
            .on('mouseout', function () {
                window.dispatchEvent(new CustomEvent('jobSelected', {detail: {jobName: undefined}}));
            });

        window.addEventListener('jobSelected', function (event) {
            const jobName = event.detail.jobName;

            svg.classed('highlighted', !!jobName);
            svg.selectAll("g")
                .classed('highlightedElement', function (d) {
                    return d.name === jobName;
                });
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
                    graphFactory.textWithLineBreaks(this, utils.breakJobName(d.name));
                }
            });
    };

    const timespanSelector = timespanSelection.create(timespanSelection.timespans.twoWeeks),
        description = graphDescription.create({
            description: "Average runtime by job for the selected interval. The job's runtime is calculated as time between start and end of its builds.",
            answer: ['Where is most of the time spent?'],
            legend: "Size: average runtime, color: job (similar colors for job group)",
            csvSource: "jobs.csv"
        }),
        graph = graphFactory.create({
            id: 'averageJobRuntime',
            headline: "Average job runtime",
            noDataReason: "provided <code>start</code> and <code>end</code> times for your builds",
            widgets: [timespanSelector.widget, description.widget]
        });

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.load('jobs?from=' + fromTimestamp, function (data) {
            graph.loaded();

            renderGraph(data, graph.svg);
        });
    });

}(timespanSelection, graphDescription, graphFactory, utils, jobColors, dataSource));
