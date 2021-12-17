(function (
    timespanSelection,
    graphDescription,
    graphFactory,
    utils,
    tooltip,
    dataSource
) {
    const margin = { top: 10, right: 0, bottom: 30, left: 35 },
        width = graphFactory.size - margin.left - margin.right,
        height = graphFactory.size - margin.top - margin.bottom;

    const x = d3.time.scale().range([0, width]);

    const y = d3.scale.linear().domain([0, 24]).range([height, 0]);

    const xAxis = d3.svg.axis().scale(x).orient("bottom");

    const yAxis = d3.svg
        .axis()
        .scale(y)
        .orient("left")
        .outerTickSize(0)
        .tickValues([0, 6, 9, 12, 15, 18])
        .tickFormat(function (d) {
            if (d < 12) {
                return d + "am";
            } else if (d === 12) {
                return d + "pm";
            } else if (d === 24) {
                return "0am";
            } else {
                return d - 12 + "pm";
            }
        });

    const timeOfDay = function (date) {
        return (
            ((date.getTime() - date.getTimezoneOffset() * 60 * 1000) %
                (24 * 60 * 60 * 1000)) /
            (60 * 60 * 1000)
        );
    };

    const startOfDay = function (date) {
        return new Date(date.getFullYear(), date.getMonth(), date.getDate());
    };

    const endOfDay = function (date) {
        const nextDay = new Date(
            date.getFullYear(),
            date.getMonth(),
            date.getDate() + 1
        );

        return new Date(nextDay.getTime() - 1);
    };

    const phasesByDay = function (start, end) {
        const phases = [];
        let startDate = new Date(start),
            endDate = end ? new Date(end) : undefined,
            endOfCurrentDay = endOfDay(startDate);

        if (endDate) {
            while (endOfCurrentDay < endDate) {
                phases.push({
                    start: startDate,
                    end: endOfCurrentDay,
                });

                startDate = new Date(endOfCurrentDay.getTime() + 1);
                endOfCurrentDay = endOfDay(startDate);
            }
        }

        phases.push({
            start: startDate,
            end: endDate,
        });

        return phases;
    };

    const flatten = function (listOfLists) {
        return listOfLists.reduce(function (a, b) {
            return a.concat(b);
        }, []);
    };

    const calculatePhasesByDay = function (data) {
        return flatten(
            data.map(function (entry) {
                return phasesByDay(entry.start, entry.end).map(function (
                    phase
                ) {
                    phase.color = entry.status === "pass" ? "green" : "red";
                    phase.culprits = entry.culprits;
                    phase.ongoingCulprits = entry.ongoingCulprits || [];
                    phase.duration = entry.end - entry.start;
                    phase.phaseStart = new Date(entry.start);
                    phase.phaseEnd = new Date(entry.end);
                    return phase;
                });
            })
        );
    };

    const annotateDateAndTime = function (phases) {
        return phases.map(function (phase) {
            phase.startOfDay = startOfDay(phase.start);
            phase.endOfDay = endOfDay(phase.end);
            phase.startTime = timeOfDay(phase.start);
            phase.endTime = timeOfDay(phase.end);
            return phase;
        });
    };

    const isWeekend = function (date) {
        const dayOfWeek = date.getDay();
        return dayOfWeek === 0 || dayOfWeek === 6;
    };

    const shortTimeString = function (date, referenceDate) {
        if (
            startOfDay(date).valueOf() === startOfDay(referenceDate).valueOf()
        ) {
            return date.toLocaleTimeString();
        } else {
            return date.toLocaleDateString() + " " + date.toLocaleTimeString();
        }
    };

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

    let axesPane;
    const getOrCreateAxesPane = function (svg) {
            if (axesPane === undefined) {
                axesPane = svg
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
                    .attr("dy", "-1.5em")
                    .attr("dx", "-1.5em")
                    .style("text-anchor", "end")
                    .text("Time of the day");
            }

            return axesPane;
        },
        removeAxesPane = function (svg) {
            svg.select("g").remove();
            axesPane = undefined;
        };

    const renderData = function (data, startTimestamp, svg) {
        const phasesByDay = annotateDateAndTime(calculatePhasesByDay(data));

        if (!phasesByDay.length) {
            removeAxesPane(svg);

            return;
        }

        const xMin =
            startTimestamp > 0
                ? startTimestamp
                : d3.min(phasesByDay, function (d) {
                      return d.startOfDay;
                  });

        x.domain([
            xMin,
            d3.max(phasesByDay, function (d) {
                return d.endOfDay;
            }),
        ]);

        const pane = getOrCreateAxesPane(svg);

        pane.selectAll(".x.axis").call(saneDayTicks(xAxis, x));

        pane.selectAll(".y.axis").call(yAxis);

        const selection = pane
            .selectAll(".entry")
            .data(phasesByDay, function (d) {
                return d.start;
            });

        selection.exit().remove();

        const g = selection.enter().append("g").attr("class", "entry");

        g.append("rect").attr("class", function (d) {
            const classNames = [];
            if (isWeekend(d.start)) {
                classNames.push("weekend");
            }
            classNames.push(d.color);

            return classNames.join(" ");
        });

        selection
            .select("rect")
            .attr("x", function (d) {
                return x(startOfDay(d.start));
            })
            .attr("width", function (d) {
                return x(d.endOfDay) - x(d.startOfDay);
            })
            .attr("y", function (d) {
                return y(d.endTime);
            })
            .attr("height", function (d) {
                return y(d.startTime) - y(d.endTime);
            });

        const tooltipText = function (d) {
            const duration = utils.formatTimeInMs(d.duration);
            let lines = [];

            lines.push(
                '<span class="label">' +
                    d.start.toLocaleDateString() +
                    "</span>"
            );
            lines.push(
                '<span class="label">from</span> ' +
                    shortTimeString(d.phaseStart, d.start)
            );
            if (!d.ongoingCulprits || d.ongoingCulprits.length === 0) {
                lines.push(
                    '<span class="label">to</span> ' +
                        shortTimeString(d.phaseEnd, d.end)
                );
            }
            lines.push("");
            lines.push('<span class="label">total</span> ' + duration);

            if (d.color === "red") {
                lines.push("");
                lines = lines.concat(
                    d.culprits.map(function (culprit) {
                        const isOngoing =
                            d.ongoingCulprits.indexOf(culprit) >= 0;
                        return (
                            '<span class="culprit">' +
                            culprit +
                            "</span>" +
                            (isOngoing
                                ? ' <span class="ongoing">(ongoing)</span>'
                                : "")
                        );
                    })
                );
            }
            return (
                '<div class="failPhasesTooltip">' +
                lines.join("<br>") +
                "</div>"
            );
        };

        tooltip.register(g, tooltipText);
    };

    const timespanSelector = timespanSelection.create(
            timespanSelection.timespans.twoMonths
        ),
        description = graphDescription.create({
            description: [
                "A failing phase starts when one job fails on an otherwise green pipeline and ends when the job is re-run with a now good outcome.",
                "The failing phase continues when another job starts to fail in between and only ends when that job also passes again.",
                "Weekends are blurred out.",
                "This graphs assumes that failures are generally not tolerated to persist over a long interval for any job,",
                "otherwise the graph will not provide much value for those periods.",
            ].join(" "),
            answer: [
                "What is the general health of the build system?",
                "How much are we stopping the pipeline?",
                "How quickly can we resume the pipeline after failure?",
            ],
            legend: "Color: healthy/broken state",
            csvSource: "failphases.csv",
        }),
        graph = graphFactory.create({
            id: "failPhases",
            headline: "Fail phases",
            noDataReason:
                "provided <code>start</code>, <code>end</code> times and the <code>outcome</code> of your builds",
            widgets: [timespanSelector.widget, description.widget],
        });

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.load("failphases?from=" + fromTimestamp, function (data) {
            graph.loaded();

            renderData(data, fromTimestamp, graph.svg);
        });
    });
})(
    timespanSelection,
    graphDescription,
    graphFactory,
    utils,
    tooltip,
    dataSource
);
