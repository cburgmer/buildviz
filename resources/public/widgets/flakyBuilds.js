(function (graphFactory, dataSource, jobColors) {
    var jobCount = 5,
        borderWidthInPx = 30,
        worstFlakyRatio = 0.10;

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
                    title: jobName + '\n\n' + flakyCount + ' flaky failures\n' + (ratio * 100).toFixed(0) + '% of the time',
                    flakyRatio: ratio,
                    value: flakyCount
                };
            });
    };

    var selectMostFlaky = function (pipeline, n) {
        var jobNames = Object.keys(pipeline);
        jobNames.sort(function (jobA, jobB) {
            return pipeline[jobA].flakyCount - pipeline[jobB].flakyCount;
        });

        var selectedPipeline = {};
        jobNames.slice(-n).forEach(function (job) {
            selectedPipeline[job] = pipeline[job];
        });
        return selectedPipeline;
    };

    var bubble = d3.layout.pack()
            .sort(null)
            .size([graphFactory.size, graphFactory.size])
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

    var color = colorScale(worstFlakyRatio);

    var timestampTwoWeeksAgo = function () {
        var today = new Date(),
            twoWeeksAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 14);
        return +twoWeeksAgo;
    };

    var renderData = function (root, svg) {
        var jobNames = Object.keys(root),
            jobColor = jobColors.colors(jobNames),
            flakyBuilds = flakyBuildsAsBubbles(selectMostFlaky(root, jobCount));

        if (!flakyBuilds.length) {
            return;
        }

        var node = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: flakyBuilds})))
                .enter()
                .append("g")
                .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });;

        node.append("title")
            .text(function(d) { return d.title; });

        node.append("circle")
            .attr("r", function (d) { return (d.r - borderWidthInPx / 2); })
            .attr("stroke-width", borderWidthInPx)
            .style("fill", function (d) {
                return jobColor(d.name);
            })
            .style("stroke", function(d) { return color(d.flakyRatio); });

        node.append("text")
            .style("text-anchor", "middle")
            .each(function (d) {
                graphFactory.textWithLineBreaks(this, d.name.split(' '));
            });
    };

    var graph = graphFactory.create({
        id: 'flakyBuilds',
        headline: "Top " + jobCount + " flaky builds",
        description: "<h3>Where are implicit dependencies not made obvious? Which jobs will probably be trusted the least?</h3><i>Border color: flaky ratio, inner color: job, diameter: flaky count</i>",
        csvUrl: "/jobs.csv",
        noDataReason: "provided the <code>outcome</code> and <code>inputs</code> for relevant builds"
    });

    graph.loading();

    dataSource.load('/jobs?from=' + timestampTwoWeeksAgo(), function (data) {
        graph.loaded();

        renderData(data, graph.svg);
    });
}(graphFactory, dataSource, jobColors));
