(function (widget) {
    // Following http://bl.ocks.org/metmajer/5480307
    var diameter = 600,
        className = "failures";

    var radius = diameter / 2,
        color = d3.scale.category20c();

    var x = d3.scale.linear()
            .range([0, 2 * Math.PI]);

    var y = d3.scale.linear()
            .range([0, radius]);

    var partition = d3.layout.partition()
            .sort(null)
            .value(function(d) { return d.size; });

    var arc = d3.svg.arc()
            .startAngle(function(d) { return Math.max(0, Math.min(2 * Math.PI, x(d.x))); })
            .endAngle(function(d) { return Math.max(0, Math.min(2 * Math.PI, x(d.x + d.dx))); })
            .innerRadius(function(d) { return Math.max(0, y(d.y)); })
            .outerRadius(function(d) { return Math.max(0, y(d.y + d.dy)); });

    var svg = widget.create("Failures", "Color: Job/Test Suite, Arc size: Number of Failures")
            .svg(diameter)
            .attr("class", className)
            .append("g")
            .attr("transform", "translate(" + diameter / 2 + "," + diameter * .52 + ")");

    var transformTestcase = function (testCase) {
        return {
            name: testCase.name,
            size: testCase.failedCount
        };
    };

    var transformTestsuite = function (suite) {
        return {
            name: suite.name,
            children: suite.children.map(function (child) {
                if (child.children) {
                    return transformTestsuite(child);
                } else {
                    return transformTestcase(child);
                }
            })
        };
    };

    var transformFailures = function (failureMap) {
        return Object.keys(failureMap)
            .filter(function (jobName) {
                var job = failureMap[jobName];
                return job.failedCount;
            })
            .map(function (jobName) {
                var job = failureMap[jobName];
                var entry = {
                    name: jobName,
                    size: job.failedCount
                };
                if (job.children) {
                    entry.children = job.children.map(transformTestsuite);
                }
                return entry;
            });
    };

    var inheritDirectParentColorForLeafs = function(d) {
        var key;
        if (d.children) {
            key = d.name;
        } else if (d.depth > 1) {
            key = d.parent.name;
        } else {
            key = d.name;
        }

        return color(key);
    };

    var computeTextRotation = function (d) {
        return (x(d.x + d.dx / 2) - Math.PI / 2) / Math.PI * 180;
    };

    d3.json('/failures', function (_, failures) {
        var data = {
            name: "Failures",
            children: transformFailures(failures)
        };

        var g = svg.datum(data).selectAll("g")
                .data(partition.nodes)
                .enter()
                .append("g");

        g.append("path")
            .attr("display", function(d) { return d.depth ? null : "none"; }) // hide inner ring
            .attr("d", arc)
            .style("stroke", "#fff")
            .style("fill", inheritDirectParentColorForLeafs);

        g.append("text")
            .attr("display", function(d) { return d.depth ? null : "none"; }) // hide inner ring
            .attr("transform", function(d) { return "rotate(" + computeTextRotation(d) + ")"; })
            .attr("x", function(d) { return y(d.y); })
            .attr("dx", "6") // margin
            .attr("dy", ".35em") // vertical-align
            .text(function(d) { return d.name; });

        g.append("title")
            .text(function (d) {
                var failures = d.size ? ' (' + d.size + ')' : '';
                return d.name + failures;
            });
    });
}(widget));
