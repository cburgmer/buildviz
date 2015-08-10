(function (widget, zoomableSunburst) {
    // Following http://bl.ocks.org/metmajer/5480307
    var diameter = 600;

    var svg = widget.create("Test failures",
                            "Color: Job/Test Suite, Arc size: Number of Failures",
                           "/failures.csv")
            .svg(diameter);

    var graph = zoomableSunburst(svg, diameter);

    var title = function (entry) {
        var failures = entry.failedCount ? ' (' + entry.failedCount + ')' : '';
        return entry.name + failures;
    };

    var transformNode = function (node) {
        var n = {
            name: node.name
        };

        if (node.children) {
            n.children = node.children.map(transformNode);
        } else {
            n.size = node.failedCount;
            n.title = title(node);
        }
        return n;
    };

    var hasOnlyOneTestSuite = function (job) {
        return job.children && job.children.length === 1 && job.children[0].children;
    };

    var skipOnlyTestSuite = function (job) {
        return hasOnlyOneTestSuite(job) ? job.children[0].children : job.children;
    };

    var transformFailures = function (failureMap) {
        return Object.keys(failureMap)
            .filter(function (jobName) {
                var job = failureMap[jobName];
                return job.failedCount;
            })
            .map(function (jobName) {
                var job = failureMap[jobName],
                    children = skipOnlyTestSuite(job);

                return {
                    name: jobName,
                    size: job.failedCount,
                    children: children.map(transformNode)
                };
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
