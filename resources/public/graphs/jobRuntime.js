(function (timespanSelection, graphFactory, utils, jobColors, dataSource) {
    var margin = {top: 10, right: 0, bottom: 30, left: 60},
        width = graphFactory.size - margin.left - margin.right,
        height = graphFactory.size - margin.top - margin.bottom;

    var x = d3.time.scale()
            .range([0, width]);

    var y = d3.scale.linear()
            .range([height, 0])
            .nice();

    var xAxis = d3.svg.axis()
            .scale(x)
            .orient("bottom");

    var yAxis = d3.svg.axis()
            .scale(y)
            .outerTickSize(0)
            .tickFormat(function (d) {
                return utils.formatTimeInMs(d * 1000);
            })
            .orient("left");

    var line = d3.svg.line()
            .interpolate("basis")
            .x(function(d) { return x(d.date); })
            .y(function(d) { return y(d.runtime); });

    var saneDayTicks = function (axis, scale) {
        var dayCount = (x.domain()[1] - x.domain()[0]) / (24 * 60 * 60 * 1000);
        if (dayCount < 10) {
            axis.ticks(d3.time.days, 1);
        } else {
            axis.ticks(10);
        }
        return axis;
    };

    var axesPane,
        getOrCreateAxesPane = function (svg) {
            if (axesPane === undefined) {
                 axesPane = svg
                        .append("g")
                        .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

                axesPane.append("g")
                    .attr("class", "x axis")
                    .attr("transform", "translate(0," + height + ")");

                axesPane.append("g")
                    .attr("class", "y axis")
                    .append("text")
                    .attr("transform", "rotate(-90)")
                    .attr("y", 6)
                    .attr("dy", ".71em")
                    .style("text-anchor", "end")
                    .text("Runtime");
            }

            return axesPane;
        },
        removeAxesPane = function (svg) {
            svg.select('g').remove();
            axesPane = undefined;
        };

    var renderData = function (data, svg) {
        if (data.length < 2) {
            removeAxesPane(svg);
            return;
        }

        var jobNames = d3.keys(data[0]).filter(function(key) { return key !== "date"; }),
            color = jobColors.colors(jobNames);

        data.forEach(function (d) {
            d.date = new Date(d.date);
        });

        var runtimes = jobNames.map(function (jobName) {
            return {
                jobName: jobName,
                values: data
                    .map(function (d) {
                        return {
                            date: d.date,
                            runtime: d[jobName] ? (new Number(d[jobName]) * 24 * 60 * 60) : undefined
                        };
                    }).filter(function (d) {
                        return d.runtime !== undefined;
                    })
            };
        });

        x.domain(d3.extent(data, function(d) { return d.date; }));
        y.domain([
            0,
            d3.max(runtimes, function(c) { return d3.max(c.values, function(v) { return v.runtime; }); })
        ]);

        var g = getOrCreateAxesPane(svg);

        g.selectAll('.x.axis')
            .call(saneDayTicks(xAxis, x));

        g.selectAll('.y.axis')
            .call(yAxis);

        var selection = g.selectAll(".job")
            .data(runtimes,
                  function (d) {
                      return d.jobName;
                  });

        selection.exit()
            .remove();

        selection.enter()
            .append("g")
            .attr("class", "job")
            .attr('data-id', function (d) {
                return 'jobname-' + d.jobName;
            })
            .append("path")
            .attr("class", "line")
            .style('stroke', function (d) {
                return color(d.jobName);
            })
            .append('title')
            .text(function (d) {
                return d.jobName;
            });

        selection.select('path')
            .attr("d", function (d) {
                return line(d.values);
            });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoMonths),
        graph = graphFactory.create({
            id: 'jobRuntime',
            headline: "Job runtime",
            description: "<h3>Is the pipeline getting faster? Has a job gotten considerably slower?</h3><i>Color: job</i>",
            csvUrl: "/jobruntime.csv",
            noDataReason: "provided <code>start</code> and <code>end</code> times for your builds over at least two consecutive days",
            widgets: [timespanSelector.widget]
        });

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.loadCSV('/jobruntime?from=' + fromTimestamp, function (data) {
            graph.loaded();

            renderData(data, graph.svg);
        });
    });
}(timespanSelection, graphFactory, utils, jobColors, dataSource));
