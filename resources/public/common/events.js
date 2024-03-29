const events = (function (utils, tooltip) {
    "use strict";

    const module = {};

    const margin = { top: 10, right: 0, bottom: 30, left: 60 },
        width = graphFactory.size - margin.left - margin.right,
        height = graphFactory.size - margin.top - margin.bottom;

    const x = d3.time.scale().range([0, width]);

    const y = d3.scale.log().rangeRound([height, 0]).clamp(true);

    const xAxis = d3.svg.axis().scale(x).orient("bottom");

    const seconds = function (value) {
        return value * 1000;
    };

    const minutes = function (value) {
        return value * 60 * 1000;
    };

    const hours = function (value) {
        return value * 60 * 60 * 1000;
    };

    const yAxis = d3.svg
        .axis()
        .scale(y)
        .outerTickSize(0)
        .tickValues([
            seconds(10),
            seconds(20),
            seconds(30),
            minutes(1),
            minutes(2),
            minutes(5),
            minutes(10),
            minutes(20),
            minutes(30),
            hours(1),
            hours(2),
            hours(5),
            hours(10),
            hours(24),
        ])
        .tickFormat(function (d) {
            return utils.formatTimeInMs(d);
        })
        .orient("left");

    const avarageLine = d3.svg
        .line()
        .x(function (d) {
            return x(d.date);
        })
        .y(function (d) {
            return y(d.value);
        });

    const saneDayTicks = function (axis, scale) {
        const dayCount =
            (x.domain()[1] - x.domain()[0]) / (24 * 60 * 60 * 1000);
        if (dayCount < 10) {
            axis.ticks(d3.time.days, 1);
        } else {
            axis.ticks(10);
        }
        return axis;
    };

    const createAxesPane = function (svg, yAxisCaption, defaultMode) {
        if (defaultMode === "lines") {
            svg.attr("class", "events linesMode");
        } else {
            svg.attr("class", "events circlesMode");
        }

        const axesPane = svg
            .append("g")
            .attr(
                "transform",
                "translate(" + margin.left + "," + margin.top + ")"
            );

        axesPane
            .append("g")
            .attr("class", "x axis")
            .attr("transform", "translate(0," + height + ")");

        axesPane
            .append("g")
            .attr("class", "y axis")
            .append("text")
            .attr("transform", "rotate(-90)")
            .attr("y", 6)
            .attr("dy", ".71em")
            .style("text-anchor", "end")
            .text(yAxisCaption);

        return axesPane;
    };

    const renderData = function (eventGroups, startTimestamp, svg, g) {
        const xMin =
            startTimestamp > 0
                ? startTimestamp
                : d3.min(eventGroups, function (c) {
                      return d3.min(c.events, function (b) {
                          return b.date;
                      });
                  });

        x.domain([
            xMin,
            d3.max(eventGroups, function (c) {
                return d3.max(c.events, function (b) {
                    return b.date;
                });
            }),
        ]);
        y.domain([
            seconds(10),
            d3.max(eventGroups, function (c) {
                return d3.max(c.events, function (b) {
                    return b.value;
                });
            }),
        ]);

        g.selectAll(".x.axis").call(saneDayTicks(xAxis, x));

        g.selectAll(".y.axis").call(yAxis);

        const selection = g
            .selectAll(".eventGroup")
            .data(eventGroups, function (d) {
                return d.id;
            });

        selection.exit().remove();

        const graph = selection
            .enter()
            .append("g")
            .attr("class", "eventGroup")
            .on("mouseover", function (d) {
                window.dispatchEvent(
                    new CustomEvent("jobSelected", {
                        detail: { jobName: d.id },
                    })
                );
            })
            .on("mouseout", function () {
                window.dispatchEvent(
                    new CustomEvent("jobSelected", {
                        detail: { jobName: undefined },
                    })
                );
            });

        window.addEventListener("jobSelected", function (event) {
            const jobName = event.detail.jobName;

            svg.classed("highlighted", !!jobName);
            svg.selectAll(".eventGroup")
                .classed("highlightedElement", function (d) {
                    return d.id === jobName;
                })
                .sort(function (a, b) {
                    // move hovered item in front
                    if (a.id !== jobName) return -1;
                    else return 1;
                });
        });

        graph.append("path").style("stroke", function (d) {
            return d.color;
        });

        const path = selection.select("path").attr("d", function (d) {
            return avarageLine(d.events);
        });

        tooltip.register(path, function (d) {
            return d.tooltip;
        });

        const circle = selection.selectAll("circle").data(function (d) {
            return d.events;
        });

        circle
            .enter()
            .append("circle")
            .attr("r", function (d) {
                return 3;
            })
            .style("fill", function (d) {
                return d.color;
            })
            .attr("class", function (d) {
                return d.highlight ? "highlighted" : "";
            });

        circle.exit().remove();

        circle
            .attr("cy", function (d) {
                return y(d.value);
            })
            .attr("cx", function (d) {
                return x(d.date);
            });

        tooltip.register(circle, function (d) {
            return d.tooltip;
        });
    };

    return function (svg, yAxisCaption, defaultMode) {
        let axesPane;
        const getOrCreateAxesPane = function () {
                if (axesPane === undefined) {
                    axesPane = createAxesPane(svg, yAxisCaption, defaultMode);
                }

                return axesPane;
            },
            removeAxesPane = function () {
                svg.select("g").remove();
                axesPane = undefined;
            };

        return {
            render: function (eventGroups, startTimestamp) {
                if (!eventGroups.length) {
                    removeAxesPane(svg);
                    return;
                }

                const g = getOrCreateAxesPane();

                renderData(eventGroups, startTimestamp, svg, g);
            },
        };
    };
})(utils, tooltip);
