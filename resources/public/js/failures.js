(function (widget, zoomableSunburst) {
    // Following http://bl.ocks.org/metmajer/5480307
    var diameter = 600,
        className = "failures";

    var svg = widget.create("Failures", "Color: Job/Test Suite, Arc size: Number of Failures")
            .svg(diameter)
            .attr("class", className);

    var graph = zoomableSunburst(svg, diameter);

    var title = function (entry) {
        var failures = entry.failedCount ? ' (' + entry.failedCount + ')' : '';
        return entry.name + failures;
    };

    var transformTestcase = function (testcase) {
        return {
            name: testcase.name,
            size: testcase.failedCount,
            title: title(testcase)
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

    d3.json('/failures', function (_, failures) {
        var data = {
            name: "Failures",
            children: transformFailures(failures)
        };

        graph.render(data);
    });
}(widget, zoomableSunburst));
