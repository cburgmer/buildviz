(function (widget, zoomableSunburst) {
    // Following http://bl.ocks.org/metmajer/5480307
    var diameter = 600;

    var svg = widget.create("Avg test runtime by class",
                            "Color: Job/Test Suite, Arc size: duration",
                            "/testclasses.csv")
            .svg(diameter);

    var graph = zoomableSunburst(svg, diameter);

    var title = function (entry) {
        var runtime = entry.averageRuntime ? ' (' + entry.averageRuntime + ' ms)' : '';
        return entry.name + runtime;
    };

    var transformTestcase = function (testcase) {
        return {
            name: testcase.name,
            size: testcase.averageRuntime,
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

    d3.json('/testclasses', function (_, testsuites) {
        var data = {
            name: "Testsuites",
            children: transformTestsuites(testsuites)
        };

        graph.render(data);
    });
}(widget, zoomableSunburst));
