const weightedTimeline = (function(tooltip, graphFactory) {
    "use strict";

    const module = {};

    const margin = { top: 20, right: 0, bottom: 10, left: 60 },
        width = graphFactory.size - margin.left - margin.right,
        height = graphFactory.size - margin.top - margin.bottom;

    const y = d3.scale.linear().range([0, height]);

    const yAxis = d3.svg
        .axis()
        .scale(y)
        .outerTickSize(0)
        .orient("left");

    const timeFormat = d3.time.format("%b %d");

    const createAxesPane = function(svg, axisCaption) {
        const axesPane = svg
            .append("g")
            .attr(
                "transform",
                "translate(" + margin.left + "," + margin.top + ")"
            );

        axesPane
            .append("g")
            .attr("class", "y axis")
            .append("text")
            .attr("dy", -10)
            .style("text-anchor", "end")
            .text(axisCaption);
        return axesPane;
    };

    const dateAtMidnight = function(fullDate) {
        return new Date(
            fullDate.getFullYear(),
            fullDate.getMonth(),
            fullDate.getDate()
        );
    };

    const isEqualDates = function(dateA, dateB) {
        return dateA.getTime() === dateB.getTime();
    };

    const extractTicks = function(data) {
        const ticks = [];

        data.forEach(function(entry) {
            const date = dateAtMidnight(entry.date),
                lastTick = ticks[ticks.length - 1];
            if (ticks.length === 0 || !isEqualDates(date, lastTick.date)) {
                ticks.push({
                    date: date,
                    offset: entry.offset
                });
            }
        });

        return ticks;
    };

    const renderData = function(data, g) {
        data.forEach(function(entry, idx) {
            if (idx === 0) {
                entry.offset = 0;
            } else {
                entry.offset = data[idx - 1].offset + data[idx - 1].value;
            }
        });

        y.domain([
            0,
            data[data.length - 1].offset + data[data.length - 1].value
        ]);

        const ticks = extractTicks(data);
        yAxis
            .tickFormat(function(tick) {
                const tickEntry = ticks.find(function(t) {
                    return tick <= t.offset;
                });
                return timeFormat(tickEntry.date);
            })
            .tickValues(
                ticks.map(function(t) {
                    return t.offset;
                })
            );

        g.selectAll(".y.axis").call(yAxis);

        const selection = g.selectAll(".bar").data(data, function(d) {
            return d.id;
        });

        selection.exit().remove();

        const node = selection
            .enter()
            .append("g")
            .attr("class", "bar")
            .on("mouseover", function(d) {
                window.dispatchEvent(
                    new CustomEvent("jobSelected", {
                        detail: { jobName: d.job }
                    })
                );
            })
            .on("mouseout", function() {
                window.dispatchEvent(
                    new CustomEvent("jobSelected", {
                        detail: { jobName: undefined }
                    })
                );
            });

        window.addEventListener("jobSelected", function(event) {
            const jobName = event.detail.jobName;

            g.classed("highlighted", !!jobName);
            g.selectAll(".bar").classed("highlightedElement", function(d) {
                return d.job === jobName;
            });
        });

        node.append("rect");
        node.append("text");

        selection
            .select("rect")
            .attr("x", 0)
            .attr("y", function(d) {
                return y(d.offset);
            })
            .attr("width", 30)
            .attr("height", function(d) {
                return y(d.value);
            })
            .attr("rx", 5)
            .attr("ry", 5)
            .style("fill", function(d) {
                return d.color;
            });

        const hasSpaceForText = function(d) {
            return y(d.value) > 12;
        };

        selection
            .select("text")
            .style("dominant-baseline", function(d) {
                return hasSpaceForText(d) ? "hanging" : "middle";
            })
            .attr("x", 38)
            .attr("y", function(d) {
                return;
            })
            .attr("y", function(d) {
                const offset = hasSpaceForText(d) ? 2 : 0;
                return y(d.offset) + offset;
            })
            .text(function(d) {
                return hasSpaceForText(d) ? d.name : "...";
            });

        const tooltipHtml = function(d) {
            return d.tooltip;
        };

        tooltip.register(selection, tooltipHtml);
    };

    return function(svg, axisCaption) {
        let axesPane;
        const getOrCreateAxesPane = function() {
                if (axesPane === undefined) {
                    axesPane = createAxesPane(svg, axisCaption);
                }

                return axesPane;
            },
            removeAxesPane = function() {
                svg.select("g").remove();
                axesPane = undefined;
            };
        svg.attr("class", "weightedTimeline");

        return {
            render: function(data) {
                if (!data.length) {
                    removeAxesPane(svg);
                    return;
                }

                const g = getOrCreateAxesPane();

                renderData(data, g);
            }
        };
    };
})(tooltip, graphFactory);
