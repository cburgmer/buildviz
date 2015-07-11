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
            .y(function(d) { return y(d.averageDuration); });

    var svg = widget.create("Fail duration",
                            "Average duration of pipeline failure",
                           "/failphases.csv")
            .svg(diameter)
            .attr('class', 'failphases');

    var g = svg
            .append("g")
            .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

    var aggregateFailPhaseDurationPerDay = function (data) {
        var failPhasesPerDay = d3.nest()
                .key(function (d) {
                    var date = new Date(d.end);
                    return new Date(date.getFullYear(), date.getMonth(), date.getDate()).valueOf();
                })
                .entries(data);

        return failPhasesPerDay.map(function (e) {
            return {
                day: new Date(parseInt(e.key)),
                averageDuration: d3.mean(e.values, function (v) {
                    return (v.end - v.start) / (60 * 1000);
                })
            };
        });
    };

    d3.json('/failphases', function (_, data) {

        var failPhaseDurationPerDay = aggregateFailPhaseDurationPerDay(data);

        x.domain(d3.extent(failPhaseDurationPerDay, function(d) { return d.day; }));
        y.domain(d3.extent(failPhaseDurationPerDay, function(d) { return d.averageDuration; }));

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

        g.selectAll('.line')
            .data([failPhaseDurationPerDay])
            .enter()
            .append("path")
            .attr("d", function(d) {
                return line(d);
            })
            .attr('class', 'line');

    });
}(widget));
