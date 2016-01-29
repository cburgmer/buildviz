var durationsByDay = (function (utils) {
    "use strict";

    var module = {};

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
            .y(function(d) { return y(d.duration); });

    var saneDayTicks = function (axis, scale) {
        var dayCount = (x.domain()[1] - x.domain()[0]) / (24 * 60 * 60 * 1000);
        if (dayCount < 10) {
            axis.ticks(d3.time.days, 1);
        } else {
            axis.ticks(10);
        }
        return axis;
    };

    var createAxesPane = function (svg, durationCaption) {
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
            .text(durationCaption);

        return axesPane;
    };

    var renderData = function (durationsByDay, svg, g) {
        svg.attr('class', 'runtimes');

        x.domain([
            d3.min(durationsByDay, function(c) { return d3.min(c.durations, function(r) { return r.date; }); }),
            d3.max(durationsByDay, function(c) { return d3.max(c.durations, function(r) { return r.date; }); })
        ]);
        y.domain([
            0,
            d3.max(durationsByDay, function(c) { return d3.max(c.durations, function(r) { return r.duration; }); })
        ]);

        g.selectAll('.x.axis')
            .call(saneDayTicks(xAxis, x));

        g.selectAll('.y.axis')
            .call(yAxis);

        var selection = g.selectAll(".entry")
            .data(durationsByDay,
                  function (d) {
                      return d.title;
                  });

        selection.exit()
            .remove();

        selection.enter()
            .append("g")
            .attr("class", "entry")
            .attr('data-id', function (d) {
                return 'jobname-' + d.title;
            })
            .append("path")
            .attr("class", "line")
            .style('stroke', function (d) {
                return d.color;
            })
            .append('title')
            .text(function (d) {
                return d.title;
            });

        selection.select('path')
            .attr("d", function (d) {
                return line(d.durations);
            });
    };

    return function (svg, durationCaption) {
        var axesPane,
            getOrCreateAxesPane = function () {
                if (axesPane === undefined) {
                    axesPane = createAxesPane(svg, durationCaption);
                }

                return axesPane;
            },
            removeAxesPane = function () {
                svg.select('g').remove();
                axesPane = undefined;
            };

        return {
            render: function (durationsByDay) {
                if (! durationsByDay.length) {
                    removeAxesPane(svg);
                    return;
                }

                var g = getOrCreateAxesPane();

                renderData(durationsByDay, svg, g);
            }
        };
    };
}(utils));
