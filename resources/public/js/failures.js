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

    var hasOnlyOneChildThatIsNotLeaf = function (node) {
        return node.children && node.children.length === 1 && node.children[0].children !== undefined;
    };

    var skipTestSuiteWithOnlyOneClassOrNestedSuite = function (testSuite) {
        var testSuiteHasOnlyOneChild = hasOnlyOneChildThatIsNotLeaf(testSuite);

        return testSuiteHasOnlyOneChild ? testSuite.children[0] : testSuite;
    };

    var skipOnlyTestSuite = function (job) {
        var hasOnlyOneTestSuite = hasOnlyOneChildThatIsNotLeaf(job);

        return hasOnlyOneTestSuite ? job.children[0].children : job.children;
    };

    var transformNode = function (node) {
        var elem = skipTestSuiteWithOnlyOneClassOrNestedSuite(node);

        var e = {
            name: elem.name
        };

        if (elem.children) {
            e.children = elem.children.map(transformNode);
        } else {
            e.size = elem.failedCount;
            e.title = title(elem);
        }
        return e;
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
                    title: jobName + ' (' + job.failedCount + ')',
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
