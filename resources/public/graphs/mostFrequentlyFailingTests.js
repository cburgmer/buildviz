(function (timespanSelection, graphFactory, zoomableSunburst, dataSource, jobColors) {
    var testCountPerJob = 5;

    var concatIds = function (parentId, id) {
        return parentId + '/' + id;
    };

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

    var excludeAlwaysGreenTests = function (tests) {
        return tests.filter(function (testCase) {
            return testCase.failedCount > 0;
        });
    };

    var filterMostNFailingTests = function (testsuites, n) {
        var tests = excludeAlwaysGreenTests(flattenTests(testsuites));

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

    var transformFailingTests = function (testcasesByJob) {
        var jobNames = testcasesByJob.map(function (jobEntry) {
            return jobEntry.jobName;
        });
        var color = jobColors.colors(jobNames);

        return testcasesByJob .map(function (jobEntry) {
            var jobName = jobEntry.jobName,
                children = skipOnlyTestSuite(filterMostNFailingTests(jobEntry.children, testCountPerJob));

            return {
                name: jobName,
                color: color(jobName),
                id: 'jobname-' + jobName,
                children: children.map(function (child) {
                    return transformNode(child, jobName);
                })
            };
        }).filter(function (entry) {
            return entry.children.length > 0;
        });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.sevenDays),
        graph = graphFactory.create({
            id: 'mostFrequentlyFailingTests',
            headline: "Most frequently failing tests",
            description: "<h3>What are the tests that provide either the most or the least feedback?</h3><i>Color: job/test suite, arc size: number of test failures</i>",
            csvUrl: "/testcases.csv",
            noDataReason: "uploaded test results",
            widgets: [timespanSelector.widget]
        });
    var sunburst = zoomableSunburst(graph.svg, graphFactory.size);

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);
        graph.loading();

        dataSource.load('/testcases?from='+ fromTimestamp, function (failures) {
            graph.loaded();

            var data = {
                name: "Most frequently failing tests",
                id: '__most_frequently_failing_tests__',
                children: transformFailingTests(failures)
            };

            sunburst.render(data);
        });
    });

}(timespanSelection, graphFactory, zoomableSunburst, dataSource, jobColors));
