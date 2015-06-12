(function (widget) {
    // Following http://bl.ocks.org/mbostock/4063423
    var diameter = 600,
        className = "failures";

    var radius = diameter / 2,
        color = d3.scale.category20c();

    var partition = d3.layout.partition()
            .sort(null)
            .size([2 * Math.PI, radius * radius])
            .value(function(d) { return 1; });

    var arc = d3.svg.arc()
            .startAngle(function(d) { return d.x; })
            .endAngle(function(d) { return d.x + d.dx; })
            .innerRadius(function(d) { return Math.sqrt(d.y); })
            .outerRadius(function(d) { return Math.sqrt(d.y + d.dy); });

    var svg = widget.create("Failures")
            .svg(diameter)
            .attr("class", className)
            .append("g")
            .attr("transform", "translate(" + diameter / 2 + "," + diameter * .52 + ")");

    // Interpolate the arcs in data space.
    var arcTween = function (a) {
        var i = d3.interpolate({x: a.x0, dx: a.dx0}, a);
        return function(t) {
            var b = i(t);
            a.x0 = b.x;
            a.dx0 = b.dx;
            return arc(b);
        };
    };

    var transformTestCase = function (testCases) {
        return testCases.map(function (testCase) {
            return {
                name: testCase.name,
                size: testCase.failedCount
            };
        });
    };

    var transformTestSuites = function (testsuites) {
        return testsuites.map(function (suite) {
            return {
                name: suite.name,
                children: transformTestCase(suite.children)
            };
        });
    };

    var transformFailures = function (failureMap) {
        return Object.keys(failureMap).map(function (jobName) {
            var job = failureMap[jobName];
            var entry = {
                name: jobName,
                size: job.failedCount
            };
            if (job.testsuites) {
                entry.children = transformTestSuites(job.testsuites);
            }
            return entry;
        });
    };

    d3.json('/failures', function (_, failures) {
        var data = {
            name: "Failures",
            children: transformFailures(failures)
        };
        var path = svg.datum(data).selectAll("path")
                .data(partition.value(function (d) { return d.size; }).nodes)
                .enter().append("path")
                .attr("display", function(d) { return d.depth ? null : "none"; }) // hide inner ring
                .attr("d", arc)
                .style("stroke", "#fff")
                .style("fill", function(d) { return color((d.children ? d : d.parent).name); })
                .style("fill-rule", "evenodd");

        path.append("title")
            .text(function (d) {
                return d.name;
            });
    });
}(widget));
