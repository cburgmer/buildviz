var events = (function (utils, tooltip) {
    "use strict";

    var module = {};

    var margin = {top: 10, right: 0, bottom: 30, left: 60},
        width = graphFactory.size - margin.left - margin.right,
        height = graphFactory.size - margin.top - margin.bottom;

    var x = d3.time.scale()
        .range([0, width]);

    var y = d3.scale.log()
        .rangeRound([height, 0])
        .clamp(true);

    var xAxis = d3.svg.axis()
        .scale(x)
        .orient("bottom");

    var seconds = function (value) {
        return value * 1000;
    };

    var minutes = function (value) {
        return value * 60 * 1000;
    };

    var hours = function (value) {
        return value * 60 * 60 * 1000;
    };

    var yAxis = d3.svg.axis()
        .scale(y)
        .outerTickSize(0)
        .tickValues([seconds(10), seconds(20), seconds(30), minutes(1), minutes(2), minutes(5), minutes(10), minutes(20), minutes(30), hours(1), hours(2), hours(5), hours(10), hours(24)])
        .tickFormat(function (d) {
            return utils.formatTimeInMs(d);
        })
        .orient("left");

    var avarageLine = d3.svg.line()
            .x(function(d) { return x(d.date); })
            .y(function(d) { return y(d.value); });

    var saneDayTicks = function (axis, scale) {
        var dayCount = (x.domain()[1] - x.domain()[0]) / (24 * 60 * 60 * 1000);
        if (dayCount < 10) {
            axis.ticks(d3.time.days, 1);
        } else {
            axis.ticks(10);
        }
        return axis;
    };

    var createAxesPane = function (svg, yAxisCaption, defaultMode) {
        if (defaultMode === 'lines') {
            svg.attr('class', 'events linesMode');
        } else {
            svg.attr('class', 'events circlesMode');
        }

        var axesPane = svg
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
            .text(yAxisCaption);

        return axesPane;
    };

    var renderData = function (eventGroups, startTimestamp, svg, g) {
        var xMin = startTimestamp > 0 ? startTimestamp : d3.min(eventGroups, function(c) { return d3.min(c.events, function (b) { return b.date; }); });

        x.domain([
            xMin,
            d3.max(eventGroups, function(c) { return d3.max(c.events, function (b) { return b.date; }); })
        ]);
        y.domain([
            seconds(10),
            d3.max(eventGroups, function(c) { return d3.max(c.events, function (b) { return b.value; }); })
        ]);

        g.selectAll('.x.axis')
            .call(saneDayTicks(xAxis, x));

        g.selectAll('.y.axis')
            .call(yAxis);

        var selection = g.selectAll(".eventGroup")
                .data(eventGroups, function (d) { return d.id; });

        selection.exit()
            .remove();

        var graph = selection.enter()
                .append("g")
                .attr('class', 'eventGroup')
                .attr('data-id', function (d) {
                    return 'jobname-' + d.id;
                });

        // move hovered item in front
        graph.on("mouseover", function(d) {
            svg.selectAll(".eventGroup").sort(function (a, b) {
                if (a.id != d.id) return -1;
                else return 1;
            });
        });

        graph.append('path')
            .style('stroke', function (d) {
                return d.color;
            });

        var path = selection
            .select('path')
            .attr('d', function (d) {
                return avarageLine(d.events);
            });

        tooltip.register(path, function (d) {
            return d.tooltip;
        });

        var circle = selection.selectAll('circle')
                .data(function (d) { return d.events; });

        circle.enter()
            .append('circle')
            .attr('r', function (d) {
                return 3;
            })
            .style('fill', function (d) {
                return d.color;
            })
            .attr('class', function (d) {
                return d.highlight ? 'highlighted' : '';
            });

        circle.exit()
            .remove();

        circle
            .attr('cy', function (d) {
                return y(d.value);
            })
            .attr('cx', function (d) {
                return x(d.date);
            });

        tooltip.register(circle, function (d) {
            return d.tooltip;
        });
    };

    return function (svg, yAxisCaption, defaultMode) {
        var axesPane,
            getOrCreateAxesPane = function () {
                if (axesPane === undefined) {
                    axesPane = createAxesPane(svg, yAxisCaption, defaultMode);
                }

                return axesPane;
            },
            removeAxesPane = function () {
                svg.select('g').remove();
                axesPane = undefined;
            };

        return {
            render: function (eventGroups, startTimestamp) {
                if (! eventGroups.length) {
                    removeAxesPane(svg);
                    return;
                }

                var g = getOrCreateAxesPane();

                renderData(eventGroups, startTimestamp, svg, g);
            }
        };
    };
}(utils, tooltip));
