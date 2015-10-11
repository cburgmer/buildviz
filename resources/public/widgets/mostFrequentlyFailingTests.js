(function (widget, zoomableSunburst, dataSource, jobColors) {
    // Following http://bl.ocks.org/metmajer/5480307
    var diameter = 600,
        testCountPerJob = 5;

    var svg = widget.create("Most frequently failing tests",
                            "Color: job/test suite, arc size: number of test failures",
                           "/failures.csv")
            .svg(diameter);

    var graph = zoomableSunburst(svg, diameter);

    var title = function (entry) {
        var failures = entry.failedCount ? ' (' + entry.failedCount + ')' : '';
        return entry.name + failures;
    };

    var hasOnlyOneChildThatIsNotLeaf = function (children) {
        return children && children.length === 1 && children[0].children !== undefined;
    };

    var skipTestSuiteWithOnlyOneClassOrNestedSuite = function (testSuite) {
        var testSuiteHasOnlyOneChild = hasOnlyOneChildThatIsNotLeaf(testSuite.children);

        return testSuiteHasOnlyOneChild ? testSuite.children[0] : testSuite;
    };

    var skipOnlyTestSuite = function (children) {
        var nonRemainderNodes = children.filter(function (c) {
            return !c.remainder;
        });
        var hasOnlyOneTestSuite = hasOnlyOneChildThatIsNotLeaf(nonRemainderNodes);

        if (hasOnlyOneTestSuite) {
            return children[0].children
                .concat(children.filter(function (c) {
                    return c.remainder;
                }));
        } else {
            return children;
        }
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

    var flattenTests = function (testsuites) {
        return testsuites.reduce(function (tests, testsuite) {
            var suiteTests = testsuite.children.reduce(function (tests, testClass) {
                return tests.concat(testClass.children.map(function (test) {
                    return {
                        name: test.name,
                        failedCount: test.failedCount,
                        testSuite: testsuite.name,
                        testClass: testClass.name
                    };
                }));
            }, []);

            return tests.concat(suiteTests);
        }, []);
    };

    var unflattenTests = function (tests) {
        return d3.nest()
            .key(function (d) {
                return d.testSuite;
            })
            .key(function (d) {
                return d.testClass;
            })
            .entries(tests)
            .map(function (suite) {
                return {
                    name: suite.key,
                    children: suite.values.map(function (testClass) {
                        return {
                            name: testClass.key,
                            children: testClass.values
                        };
                    })
                };
            });
    };

    var filterMostNFailingTests = function (testsuites, n) {
        var tests = flattenTests(testsuites);

        tests.sort(function (a, b) {
            return a.failedCount - b.failedCount;
        });

        var slowestTests = tests.slice(-n),
            remaindingTotalFailedCount = tests.slice(0, -n).reduce(function (totalFailedCount, test) {
                return totalFailedCount + test.failedCount;
            }, 0);

        var suites = unflattenTests(slowestTests);

        if (remaindingTotalFailedCount) {
            suites.push({
                name: 'Remaining average',
                failedCount: remaindingTotalFailedCount / (tests.length - n),
                remainder: true
            });
        }

        return suites;
    };

    var transformFailures = function (failureMap) {
        var jobNames = Object.keys(failureMap),
            color = jobColors.colors(jobNames);

        return Object.keys(failureMap)
            .filter(function (jobName) {
                var job = failureMap[jobName];
                return job.failedCount;
            })
            .map(function (jobName) {
                var job = failureMap[jobName],
                    children = skipOnlyTestSuite(filterMostNFailingTests(job.children, testCountPerJob));

                return {
                    name: jobName,
                    color: color(jobName),
                    id: 'jobname-' + jobName,
                    children: children.map(transformNode)
                };
            });
    };

    var timestampOneWeekAgo = function () {
        var today = new Date(),
            oneWeekAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 7);
        return +oneWeekAgo;
    };

    dataSource.load('/failures?from='+ timestampOneWeekAgo(), function (failures) {
        var data = {
            name: "Most frequently failing tests",
            children: transformFailures(failures)
        };

        graph.render(data);
    });
}(widget, zoomableSunburst, dataSource, jobColors));
