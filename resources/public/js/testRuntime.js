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

    var transformTestcase = function (testcase) {
        return {
            name: testcase.name,
            size: testcase.averageRuntime
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

    var transformTestsuites = function (jobMap) {
        return Object.keys(jobMap)
            .map(function (jobName) {
                var job = jobMap[jobName];
                return {
                    name: jobName,
                    children: job.children.map(transformTestsuite)
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

    var arcTween = function (d) {
        var xd = d3.interpolate(x.domain(), [d.x, d.x + d.dx]),
            yd = d3.interpolate(y.domain(), [d.y, 1]),
            yr = d3.interpolate(y.range(), [d.y ? 20 : 0, radius]);
        return function(d, i) {
            return i
                ? function(t) { return arc(d); }
            : function(t) { x.domain(xd(t)); y.domain(yd(t)).range(yr(t)); return arc(d); };
        };
    };

    d3.json('/testsuites', function (_, testsuites) {
        var data = {
            name: "Testsuites",
            children: transformTestsuites(testsuites)
        };

        var click = function (d) {
            text.transition().attr("opacity", 0);

            path.transition()
                .duration(750)
                .attrTween("d", arcTween(d))
                .each("end", function(e, i) {
                    // check if the animated element's data e lies within the visible angle span given in d
                    if (e.x >= d.x && e.x < (d.x + d.dx)) {
                        d3.select(this.parentNode).select("text")
                            .attr("opacity", 1)
                            .attr("transform", function() { return "rotate(" + computeTextRotation(e) + ")"; })
                            .attr("x", function(d) { return y(d.y); });
                    }
                });
        };

        var g = svg.datum(data).selectAll("g")
                .data(partition.nodes)
                .enter()
                .append("g");

        var path = g.append("path")
                .attr("d", arc)
                .style("stroke", "#fff")
                .style("fill", function (d) {
                    if (d.depth) {
                        return inheritDirectParentColorForLeafs(d);
                    } else {
                        return 'transparent';
                    }
                })
                .style('cursor', 'pointer')
                .on('click', click);

        var text = g.append("text")
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
