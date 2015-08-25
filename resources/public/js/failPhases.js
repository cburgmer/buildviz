(function (widget, utils) {
    var diameter = 600;

    var margin = {top: 10, right: 0, bottom: 30, left: 50},
        width = diameter - margin.left - margin.right,
        height = diameter - margin.top - margin.bottom;

    var x = d3.time.scale()
            .range([0, width]);

    var y = d3.scale.linear()
            .range([height, 0]);

    var y2 = d3.scale.linear()
            .domain([0, 24])
            .range([height, 0]);

    var xAxis = d3.svg.axis()
            .scale(x)
            .orient("bottom");

    var yAxis = d3.svg.axis()
            .scale(y)
            .orient("left");

    var y2Axis = d3.svg.axis()
            .scale(y2)
            .orient("left");

    var line = d3.svg.line()
            .interpolate("basis")
            .x(function(d) { return x(d.day); })
            .y(function(d) { return y(d.duration || 0); });

    var svg = widget.create("Fail phases",
                            "Pipeline failure vs. green phases",
                           "/failphases.csv")
            .svg(diameter)
            .attr('class', 'failphases');

    var g = svg
            .append("g")
            .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

    var durationInMinutes = function (duration) {
        return duration / (60 * 1000);
    };

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
            endDate = new Date(end),
            endOfCurrentDay = endOfDay(startDate);

        while (endOfCurrentDay < endDate) {
            phases.push({
                start: startDate,
                end: endOfCurrentDay
            });

            startDate = new Date(endOfCurrentDay.getTime() + 1);
            endOfCurrentDay = endOfDay(startDate);
        }

        phases.push({
            start: startDate,
            end: endDate
        });

        return phases;
    };

    var flatten = function (listOfLists) {
        return listOfLists.reduce(function (a, b) { return a.concat(b); });
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
                    return phase;
                });
            }

            lastEntry = entry;

            return greenPhases.concat(phasesByDay(entry.start, entry.end).map(function (phase) {
                phase.color = 'red';
                phase.culprits = entry.culprits;
                phase.duration = entry.end - entry.start;
                return phase;
            }));
        }));
    };

    var calculatePhases = function (data) {
        var lastPhase;

        return data.map(function (phase) {
            var calculatedPhase = {
                end: phase.end
            };

            if (lastPhase) {
                calculatedPhase.previousGreenPhaseDuration = phase.start - lastPhase.end;
            }
            calculatedPhase.redPhaseDuration = phase.end - phase.start;

            lastPhase = phase;

            return calculatedPhase;
        });
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

    var aggregatePhaseDurationByDay = function (phases) {
        var phasesByDay = d3.nest()
                .key(function (d) {
                    var date = new Date(d.end);
                    return new Date(date.getFullYear(), date.getMonth(), date.getDate()).valueOf();
                })
                .entries(phases);

        return phasesByDay.map(function (e) {
            return {
                day: new Date(parseInt(e.key)),
                redDuration: d3.mean(e.values.map(function (v) { return v.redPhaseDuration; }), durationInMinutes),
                greenDuration: d3.mean(e.values.map(function (v) { return v.previousGreenPhaseDuration; }), durationInMinutes)
            };
        });
    };

    d3.json('/failphases', function (_, data) {
        var phasesByDay = annotateDateAndTime(calculatePhasesByDay(data));
        // var phaseDurationByDay = aggregatePhaseDurationByDay(calculatePhases(data));

        x.domain(d3.extent(phasesByDay, function(d) { return d.startOfDay; }));
        // y.domain(d3.extent(phaseDurationByDay, function(d) { return d.redDuration; }));

        g.append("g")
            .attr("class", "x axis")
            .attr("transform", "translate(0," + height + ")")
            .call(xAxis);

        // g.append("g")
        //     .attr("class", "y axis")
        //     .call(yAxis)
        //     .append("text")
        //     .attr("transform", "rotate(-90)")
        //     .attr("y", 6)
        //     .attr("dy", ".71em")
        //     .style("text-anchor", "end")
        //     .text("Duration [minutes]");

        g.append("g")
            .attr("class", "y axis")
            .call(y2Axis)
            .append("text")
            .attr("transform", "rotate(-90)")
            .attr("y", 6)
            .attr("dy", ".71em")
            .style("text-anchor", "end")
            .text("Time of the day");

        g.selectAll('rect')
            .data(phasesByDay)
            .enter()
            .append('rect')
            .attr('x', function (d) {
                return x(startOfDay(d.start));
            })
            .attr('width', function (d) {
              return x(d.endOfDay) - x(d.startOfDay);
            })
            .attr('y', function (d) {
                return y2(d.endTime);
            })
            .attr('height', function (d) {
                return y2(d.startTime) - y2(d.endTime);
            })
            .attr('fill', function (d) {
                return d.color;
            })
            .attr('title', function (d) {
                var duration = utils.formatTimeInMs(d.duration);
                if (d.color === 'red') {
                    return duration + '\n' + d.culprits.join(', ');
                }
                return duration;
            });

        // g.selectAll('.redLine')
        //     .data([phaseDurationByDay.map(function (d) { return {day: d.day, duration: d.redDuration}; })])
        //     .enter()
        //     .append("path")
        //     .attr("d", line)
        //     .attr('class', 'redLine');

        // g.selectAll('.greenLine')
        //     .data([phaseDurationByDay.map(function (d) { return {day: d.day, duration: d.greenDuration}; })])
        //     .enter()
        //     .append("path")
        //     .attr("d", line)
        //     .attr('class', 'greenLine');

    });
}(widget, utils));
