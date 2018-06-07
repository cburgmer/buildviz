var weightedTimeline = (function (tooltip, graphFactory) {
    "use strict";

    var module = {};

    var margin = {top: 20, right: 0, bottom: 10, left: 60},
        width = graphFactory.size - margin.left - margin.right,
        height = graphFactory.size - margin.top - margin.bottom;

    var y = d3.scale.linear().range([0, height]);

    var yAxis = d3.svg.axis()
        .scale(y)
        .outerTickSize(0)
        .orient("left");

    var timeFormat = d3.time.format('%b %d');

    var createAxesPane = function (svg, axisCaption) {
        var axesPane = svg
                .append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

        axesPane.append("g")
            .attr("class", "y axis")
            .append("text")
            .attr("dy", -10)
            .style("text-anchor", "end")
            .text(axisCaption);
        return axesPane;
    };

    var dateAtMidnight = function (fullDate) {
        return new Date(fullDate.getFullYear(), fullDate.getMonth(), fullDate.getDate());
    };

    var isEqualDates = function (dateA, dateB) {
        return dateA.getTime() === dateB.getTime();
    };

    var extractTicks = function (data) {
        var ticks = [];

        data.forEach(function (entry) {
            var date = dateAtMidnight(entry.date),
                lastTick = ticks[ticks.length - 1];
            if (ticks.length === 0 ||
                ! isEqualDates(date, lastTick.date)) {
                ticks.push({
                    date: date,
                    offset: entry.offset
                });
            }
        });

        return ticks;
    };

    var renderData = function (data, g) {
        data.forEach(function(entry, idx) {
            if (idx === 0) {
                entry.offset = 0;
            } else {
                entry.offset = data[idx - 1].offset + data[idx - 1].value;
            }
        });

        y.domain([0, data[data.length - 1].offset + data[data.length - 1].value]);

        var ticks = extractTicks(data);
        yAxis
            .tickFormat(function (tick) {
                var tickEntry = ticks.find(function (t) { return tick <= t.offset; });
                return timeFormat(tickEntry.date);
            })
            .tickValues(ticks.map(function (t) { return t.offset; }));

        g.selectAll('.y.axis')
            .call(yAxis);

        var selection = g.selectAll(".bar")
            .data(data, function (d) { return d.id; });

        selection.exit()
            .remove();

        var node = selection
            .enter()
            .append("g")
            .attr("class", "bar");

        node.append("rect");
        node.append("text");

        selection.select("rect")
            .attr("x", 0)
            .attr("y", function(d) { return y(d.offset); })
            .attr("width", 30)
            .attr("height", function (d) { return y(d.value); })
            .attr('rx', 5)
            .attr('ry', 5)
            .style('fill', function (d) {
                return d.color;
            });

        var hasSpaceForText = function (d) {
            return y(d.value) > 12;
        };

        selection.select("text")
            .style('dominant-baseline', function (d) {
                return hasSpaceForText(d) ? 'hanging' : 'middle';
            })
            .attr("x", 38)
            .attr("y", function(d) { return ; })
            .attr('y', function (d) {
                var offset = hasSpaceForText(d) ? 2 : 0;
                return y(d.offset) + offset;
            })
            .text(function (d) {
                return hasSpaceForText(d) ? d.name : '...';
            });

        var tooltipHtml = function (d) {
            return d.tooltip;
        };

        tooltip.register(selection, tooltipHtml);
    };

    return function (svg, axisCaption) {
        var axesPane,
            getOrCreateAxesPane = function () {
                if (axesPane === undefined) {
                    axesPane = createAxesPane(svg, axisCaption);
                }

                return axesPane;
            },
            removeAxesPane = function () {
                svg.select('g').remove();
                axesPane = undefined;
            };
        svg.attr('class', 'weightedTimeline');

        return {
            render: function (data) {
                if (! data.length) {
                    removeAxesPane(svg);
                    return;
                }

                var g = getOrCreateAxesPane();

                renderData(data, g);
            }
        };
    };
}(tooltip, graphFactory));
