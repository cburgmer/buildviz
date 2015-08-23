(function (widget) {
    var diameter = 600;

    var margin = {top: 10, right: 0, bottom: 30, left: 50},
        width = diameter - margin.left - margin.right,
        height = diameter - margin.top - margin.bottom;

    var x = d3.time.scale()
            .range([0, width]);

    var y = d3.scale.linear()
            .range([height, 0]);

    var xAxis = d3.svg.axis()
            .scale(x)
            .orient("bottom");

    var yAxis = d3.svg.axis()
            .scale(y)
            .orient("left");

    var line = d3.svg.line()
            .interpolate("basis")
            .x(function(d) { return x(d.day); })
            .y(function(d) { return y(d.duration || 0); });

    var svg = widget.create("Fail duration",
                            "Average duration of pipeline failure vs. green phases",
                           "/failphases.csv")
            .svg(diameter)
            .attr('class', 'failphases');

    var g = svg
            .append("g")
            .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

    var durationInMinutes = function (duration) {
        return duration / (60 * 1000);
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

        var phaseDurationByDay = aggregatePhaseDurationByDay(calculatePhases(data));

        x.domain(d3.extent(phaseDurationByDay, function(d) { return d.day; }));
        y.domain(d3.extent(phaseDurationByDay, function(d) { return d.redDuration; }));

        g.append("g")
            .attr("class", "x axis")
            .attr("transform", "translate(0," + height + ")")
            .call(xAxis);

        g.append("g")
            .attr("class", "y axis")
            .call(yAxis)
            .append("text")
            .attr("transform", "rotate(-90)")
            .attr("y", 6)
            .attr("dy", ".71em")
            .style("text-anchor", "end")
            .text("Duration [minutes]");

        g.selectAll('.redLine')
            .data([phaseDurationByDay.map(function (d) { return {day: d.day, duration: d.redDuration}; })])
            .enter()
            .append("path")
            .attr("d", line)
            .attr('class', 'redLine');

        g.selectAll('.greenLine')
            .data([phaseDurationByDay.map(function (d) { return {day: d.day, duration: d.greenDuration}; })])
            .enter()
            .append("path")
            .attr("d", line)
            .attr('class', 'greenLine');

    });
}(widget));
