(function (timespanSelection, graphDescription, graphFactory, utils, dataSource) {
    var margin = {top: 10, right: 0, bottom: 30, left: 35},
        width = graphFactory.size - margin.left - margin.right,
        height = graphFactory.size - margin.top - margin.bottom;

    var x = d3.time.scale()
            .range([0, width]);

    var y = d3.scale.linear()
            .domain([0, 24])
            .range([height, 0]);

    var xAxis = d3.svg.axis()
            .scale(x)
            .orient("bottom");

    var yAxis = d3.svg.axis()
            .scale(y)
            .orient("left")
            .outerTickSize(0)
            .tickValues([0, 6, 9, 12, 15, 18])
            .tickFormat(function (d) {
                if (d < 12) {
                    return d + 'am';
                } else if (d === 12) {
                    return d + 'pm';
                } else if (d === 24) {
                    return '0am';
                } else {
                    return (d - 12) + 'pm';
                }
            });

    var timeOfDay = function (date) {
        return (date.getTime() - (date.getTimezoneOffset() * 60 * 1000)) % (24 * 60 * 60 * 1000) / (60 * 60 * 1000);
    };

    var startOfDay = function (date) {
        return new Date(date.getFullYear(), date.getMonth(), date.getDate());
    };

    var endOfDay = function (date) {
        var nextDay = new Date(date.getFullYear(), date.getMonth(), date.getDate() + 1);

        return new Date(nextDay.getTime() - 1);
    };

    var phasesByDay = function (start, end) {
        var phases = [],
            startDate = new Date(start),
            endDate = end ? new Date(end) : undefined,
            endOfCurrentDay = endOfDay(startDate);

        if (endDate) {
            while (endOfCurrentDay < endDate) {
                phases.push({
                    start: startDate,
                    end: endOfCurrentDay
                });

                startDate = new Date(endOfCurrentDay.getTime() + 1);
                endOfCurrentDay = endOfDay(startDate);
            }
        }

        phases.push({
            start: startDate,
            end: endDate
        });

        return phases;
    };

    var flatten = function (listOfLists) {
        return listOfLists.reduce(function (a, b) { return a.concat(b); }, []);
    };

    var calculatePhasesByDay = function (data) {
        var lastEntry;

        return flatten(data.map(function (entry) {
            var greenPhases = [],
                start = entry.start;

            if (lastEntry) {
                greenPhases = phasesByDay(lastEntry.end + 1, entry.start - 1).map(function (phase) {
                    phase.color = 'green';
                    phase.duration = entry.start - lastEntry.end;
                    phase.phaseStart = new Date(lastEntry.end);
                    phase.phaseEnd = new Date(entry.start);
                    return phase;
                });
            }

            lastEntry = entry;

            var redPhases = phasesByDay(entry.start, entry.end).filter(function (phase) {
                return phase.end !== undefined;
            }).map(function (phase) {
                phase.color = 'red';
                phase.culprits = entry.culprits;
                phase.duration = entry.end - entry.start;
                phase.phaseStart = new Date(entry.start);
                phase.phaseEnd = new Date(entry.end);
                return phase;
            });

            return greenPhases.concat(redPhases);
        }));
    };

    var annotateDateAndTime = function (phases) {
        return phases.map(function (phase) {
            phase.startOfDay = startOfDay(phase.start);
            phase.endOfDay = endOfDay(phase.end);
            phase.startTime = timeOfDay(phase.start);
            phase.endTime = timeOfDay(phase.end);
            return phase;
        });
    };

    var isWeekend = function (date) {
        var dayOfWeek = date.getDay();
        return dayOfWeek === 0 || dayOfWeek === 6;
    };

    var shortTimeString = function (date, referenceDate) {
        if (startOfDay(date).valueOf() === startOfDay(referenceDate).valueOf()) {
            return date.toLocaleTimeString();
        } else {
            return date.toLocaleTimeString() + ' (' + date.toLocaleDateString() + ')';
        }
    };

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
                    .attr("dy", "-1.5em")
                    .attr("dx", "-1.5em")
                    .style("text-anchor", "end")
                    .text("Time of the day");
            }

            return axesPane;
        },
        removeAxesPane = function (svg) {
            svg.select('g').remove();
            axesPane = undefined;
        };

    var renderData = function (data, svg) {
        var phasesByDay = annotateDateAndTime(calculatePhasesByDay(data));

        if (!phasesByDay.length) {
            removeAxesPane(svg);

            return;
        }

        x.domain([d3.min(phasesByDay, function(d) { return d.startOfDay; }),
                  d3.max(phasesByDay, function(d) { return d.endOfDay; })]);

        var g = getOrCreateAxesPane(svg);

        g.selectAll('.x.axis')
            .call(saneDayTicks(xAxis, x));

        g.selectAll('.y.axis')
            .call(yAxis);

        var selection = g.selectAll('rect')
            .data(phasesByDay,
                  function (d) {
                      return d.start;
                  });

        selection.exit()
            .remove();

        selection
            .enter()
            .append('rect')
            .attr('class', function (d) {
                var classNames = [];
                if (isWeekend(d.start)) {
                    classNames.push('weekend');
                }
                classNames.push(d.color);

                return classNames.join(' ');
            })
            .attr('title', function (d) {
                var duration = utils.formatTimeInMs(d.duration),
                    lines = [];

                lines.push(d.start.toLocaleDateString());
                lines.push('start ' + shortTimeString(d.phaseStart, d.start));
                lines.push('end ' + shortTimeString(d.phaseEnd, d.end));
                lines.push('');
                lines.push('for ' + duration);

                if (d.color === 'red') {
                    lines.push('');
                    lines = lines.concat(d.culprits);
                }
                return lines.join('\n');
            });

        selection
            .attr('x', function (d) {
                return x(startOfDay(d.start));
            })
            .attr('width', function (d) {
                return x(d.endOfDay) - x(d.startOfDay);
            })
            .attr('y', function (d) {
                return y(d.endTime);
            })
            .attr('height', function (d) {
                return y(d.startTime) - y(d.endTime);
            });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoMonths),
        description = graphDescription.create({
            description: 'A failing phase starts when one job fails on an otherwise green pipeline and ends when the job is re-run with a now good outcome. The failing phase continues when another job starts to fail in between and only ends when that job also passes again. Weekends are blured out. This graphs assumes that failures are generally not tolerated to persist over a long interval for any job, otherwise the graph will not provide much value for those periods.',
            answer: ["What is the general health of the build system?",
                     "How much are we stopping the pipeline?",
                     "How quickly can we resume the pipeline after failure?"],
            legend: "Color: healthy/broken state",
            csvSource: "/failphases.csv"
        }),
        graph = graphFactory.create({
            id: 'failPhases',
            headline: "Fail phases",
            noDataReason: "provided <code>start</code>, <code>end</code> times and the <code>outcome</code> of your builds",
            widgets: [timespanSelector.widget, description.widget]
        });

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.load('/failphases?from=' + fromTimestamp, function (data) {
            graph.loaded();

            renderData(data, graph.svg);
        });
    });

}(timespanSelection, graphDescription, graphFactory, utils, dataSource));
