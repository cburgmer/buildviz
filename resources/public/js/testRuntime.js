(function (widget, zoomableSunburst, utils) {
    // Following http://bl.ocks.org/metmajer/5480307
    var diameter = 600;

    var svg = widget.create("Avg test runtime by class",
                            "Color: Job/Test Suite, Arc size: duration",
                            "/testclasses.csv")
            .svg(diameter);

    var graph = zoomableSunburst(svg, diameter);

    var title = function (entry) {
        var runtime = entry.averageRuntime ? ' (' + utils.formatTimeInMs(entry.averageRuntime, {showMillis: true}) + ')' : '';
        return entry.name + runtime;
    };

    var hasOnlyOneChild = function (node) {
        return node.children && node.children.length === 1;
    };

    var skipTestSuiteWithOnlyOneClassOrNestedSuite = function (testSuite) {
        var testSuiteHasOnlyOneChild = hasOnlyOneChild(testSuite);

        return testSuiteHasOnlyOneChild ? testSuite.children[0] : testSuite;
    };

    var skipOnlyTestSuite = function (job) {
        var hasOnlyOneTestSuite = hasOnlyOneChild(job);

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
            e.size = elem.averageRuntime;
            e.title = title(elem);
        }
        return e;
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
}(widget, zoomableSunburst, utils));
