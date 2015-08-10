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

    var transformNode = function (node) {
        var n = {
            name: node.name
        };

        if (node.children) {
            n.children = node.children.map(transformNode);
        } else {
            n.size = node.averageRuntime;
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

    var transformTestsuites = function (jobMap) {
        return Object.keys(jobMap)
            .map(function (jobName) {
                var job = jobMap[jobName],
                    children = skipOnlyTestSuite(job);

                return {
                    name: jobName,
                    children: children.map(transformNode)
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
