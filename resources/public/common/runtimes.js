var runtimes = (function (utils) {
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

    module.renderData = function (runtimes, svg) {
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

        if (! runtimes.length) {
            removeAxesPane(svg);
            return;
        }

        svg.attr('class', 'runtimes');

        x.domain([
            d3.min(runtimes, function(c) { return d3.min(c.runtimes, function(r) { return r.date; }); }),
            d3.max(runtimes, function(c) { return d3.max(c.runtimes, function(r) { return r.date; }); })
        ]);
        y.domain([
            0,
            d3.max(runtimes, function(c) { return d3.max(c.runtimes, function(r) { return r.runtime; }); })
        ]);

        var g = getOrCreateAxesPane(svg);

        g.selectAll('.x.axis')
            .call(saneDayTicks(xAxis, x));

        g.selectAll('.y.axis')
            .call(yAxis);

        var selection = g.selectAll(".entry")
            .data(runtimes,
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
                return line(d.runtimes);
            });
    };

    return module;
}(utils));
