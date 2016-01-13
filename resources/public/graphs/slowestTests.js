(function (timespanSelection, graphFactory, zoomableSunburst, dataSource, jobColors, utils) {
    var testCountPerJob = 5;

    var concatIds = function (parentId, id) {
        return parentId + '/' + id;
    };

    var title = function (entry) {
        return entry.name + ' (' + utils.formatTimeInMs(entry.averageRuntime, {showMillis: true}) + ')';
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

    var transformNode = function (node, parentId) {
        var elem = skipTestSuiteWithOnlyOneClassOrNestedSuite(node),
            id = concatIds(parentId, elem.name);

        var e = {
            name: elem.name,
            id: id
        };

        if (elem.children) {
            e.children = elem.children.map(function (child) {
                return transformNode(child, id);
            });
        } else {
            e.size = elem.averageRuntime;
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
                        averageRuntime: test.averageRuntime,
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

    var filterNSlowestTests = function (testsuites, n) {
        var tests = flattenTests(testsuites);

        tests.sort(function (a, b) {
            return a.averageRuntime - b.averageRuntime;
        });

        var slowestTests = tests.slice(-n),
            remaindingTotalAverageRuntime = tests.slice(0, -n).reduce(function (totalAverageRuntime, test) {
                return totalAverageRuntime + test.averageRuntime;
            }, 0);

        var suites = unflattenTests(slowestTests);

        if (remaindingTotalAverageRuntime) {
            suites.push({
                name: 'Remaining average',
                averageRuntime: remaindingTotalAverageRuntime / (tests.length - n),
                remainder: true
            });
        }

        return suites;
    };

    var transformTestCases = function (testcasesByJob) {
        var jobNames = testcasesByJob.map(function (jobEntry) {
            return jobEntry.jobName;
        });
        var color = jobColors.colors(jobNames);

        return testcasesByJob.map(function (jobEntry) {
            var jobName = jobEntry.jobName,
                children = skipOnlyTestSuite(filterNSlowestTests(jobEntry.children, testCountPerJob));

            return {
                name: jobName,
                color: color(jobName),
                id: 'jobname-' + jobName,
                children: children.map(function (child) {
                    return transformNode(child, jobName);
                })
            };
        });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.sevenDays),
        graph = graphFactory.create({
            id: 'slowestTests',
            headline: "Top 5 slowest test cases by job",
            description: "<h3>What could be the first place to look at to improve test runtime?</h3><i>Color: job/test suite, arc size: test runtime</i>",
            csvUrl: "/testcases.csv",
            noDataReason: "uploaded test results",
            widgets: [timespanSelector.widget]
        });
    var sunburst = zoomableSunburst(graph.svg, graphFactory.size);

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);
        graph.loading();

        dataSource.load('/testcases?from='+ fromTimestamp, function (testCases) {
            graph.loaded();

            var data = {
                name: "Tests",
                id: '__tests__',
                children: transformTestCases(testCases)
            };

            sunburst.render(data);
        });
    });

}(timespanSelection, graphFactory, zoomableSunburst, dataSource, jobColors, utils));
