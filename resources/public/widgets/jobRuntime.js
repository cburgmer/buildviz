(function (widget, utils, jobColors) {
    var diameter = 600;

    var margin = {top: 10, right: 0, bottom: 30, left: 60},
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
            .tickFormat(function (d) {
                return utils.formatTimeInMs(d * 24 * 60 * 60 * 1000);
            })
            .orient("left");

    var line = d3.svg.line()
            .interpolate("basis")
            .x(function(d) { return x(d.date); })
            .y(function(d) { return y(d.runtime); });

    var svg = widget.create("Job runtime",
                            "Runtime of all jobs",
                            "/pipelineruntime.csv")
            .svg(diameter)
            .attr('class', 'pipelineRuntime');

    d3.csv('/pipelineruntime', function (_, data) {
        var jobNames = d3.keys(data[0]).filter(function(key) { return key !== "date"; });

        if (!jobNames.length) {
            return;
        }

        var color = jobColors.colors(jobNames);

        data.forEach(function (d) {
            d.date = new Date(d.date);
        });

        var runtimes = jobNames.map(function (jobName) {
            return {
                jobName: jobName,
                values: data
                    .map(function (d) {
                        return {
                            date: d.date,
                            runtime: d[jobName] ? new Number(d[jobName]) : undefined
                        };
                    }).filter(function (d) {
                        return d.runtime !== undefined;
                    })
            };
        });

        x.domain(d3.extent(data, function(d) { return d.date; }));
        y.domain([
            0,
            d3.max(runtimes, function(c) { return d3.max(c.values, function(v) { return v.runtime; }); })
        ]);

        var g = svg
                .append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

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
            .text("Runtime");

        g.selectAll(".job")
            .data(runtimes)
            .enter()
            .append("g")
            .attr("class", "job")
            .append("path")
            .attr("class", "line")
            .attr("d", function (d) {
                return line(d.values);
            })
            .style('stroke', function (d) {
                return color(d.jobName);
            })
            .append('title')
            .text(function (d) {
                return d.jobName;
            });
    });
}(widget, utils, jobColors));
