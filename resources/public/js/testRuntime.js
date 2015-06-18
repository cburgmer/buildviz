(function (widget) {
    // Following http://bl.ocks.org/metmajer/5480307
    var diameter = 600,
        className = "testRuntime";

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

    var svg = widget.create("Avg test runtime", "Color: Job/Test Suite, Arc size: duration")
            .svg(diameter)
            .attr("class", className)
            .append("g")
            .attr("transform", "translate(" + diameter / 2 + "," + diameter * .52 + ")");

    var transformTestCase = function (testCase) {
        return {
            name: testCase.name,
            size: testCase.averageRuntime
        };
    };

    var transformTestSuite = function (suite) {
        return {
            name: suite.name,
            children: suite.children.map(function (child) {
                if (child.children) {
                    return transformTestSuite(child);
                } else {
                    return transformTestCase(child);
                }
            })
        };
    };

    var transformTestsuites = function (jobMap) {
        return Object.keys(jobMap)
            .map(function (jobName) {
                var job = jobMap[jobName];
                return {
                    name: jobName,
                    children: job.children.map(transformTestSuite)
                };
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

    d3.json('/testsuites', function (_, testsuites) {
        var data = {
            name: "Testsuites",
            children: transformTestsuites(testsuites)
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
                var runtime = d.size ? ' (' + d.size + ' ms)' : '';
                return d.name + runtime;
            });
    });
}(widget));
