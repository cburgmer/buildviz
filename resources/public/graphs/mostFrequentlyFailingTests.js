(function (timespanSelection, graphDescription, graphFactory, zoomableSunburst, dataSource, jobColors) {
    var testCountPerJob = 5;

    var concatIds = function (ids) {
        return ids.join('/');
    };

    var title = function (entry) {
        var failures = entry.failedCount ? ' (' + entry.failedCount + ')' : '';
        return [entry.name + failures,
                entry.testClass,
                entry.testSuite].join('\n');
    };

    var transformTestCase = function (testCase, parentId) {
        return {
            name: testCase.name,
            size: testCase.failedCount,
            title: title(testCase),
            id: concatIds([parentId, testCase.testSuite, testCase.testClass, testCase.name])
        };
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

    var excludeAlwaysGreenTests = function (tests) {
        return tests.filter(function (testCase) {
            return testCase.failedCount > 0;
        });
    };

    var mostNFailingTestCases = function (testsuites, n) {
        var tests = excludeAlwaysGreenTests(flattenTests(testsuites));

        tests.sort(function (a, b) {
            return a.failedCount - b.failedCount;
        });

        var mostFailingTests = tests.slice(-n),
            remaindingTotalFailedCount = tests.slice(0, -n).reduce(function (totalFailedCount, test) {
                return totalFailedCount + test.failedCount;
            }, 0);

        if (remaindingTotalFailedCount) {
            mostFailingTests.push({
                name: 'Remaining average',
                failedCount: remaindingTotalFailedCount / (tests.length - n),
                remainder: true
            });
        }

        return mostFailingTests;
    };

    var transformFailingTests = function (testcasesByJob) {
        var jobNames = testcasesByJob.map(function (jobEntry) {
            return jobEntry.jobName;
        });
        var color = jobColors.colors(jobNames);

        return testcasesByJob .map(function (jobEntry) {
            var jobName = jobEntry.jobName,
                children = mostNFailingTestCases(jobEntry.children, testCountPerJob);

            return {
                name: jobName,
                color: color(jobName),
                id: 'jobname-' + jobName,
                children: children.map(function (child) {
                    return transformTestCase(child, jobName);
                })
            };
        }).filter(function (entry) {
            return entry.children.length > 0;
        });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.sevenDays),
        description = graphDescription.create({
            description: "The 5 test cases with the most failures by job. Multiple test cases with the same name have their failures added up.",
            answer: ["What are the tests that provide either the most or the least feedback?"],
            legend: "Color: job/test suite, arc size: number of test failures",
            csvSource: "testcases.csv"
        }),
        graph = graphFactory.create({
            id: 'mostFrequentlyFailingTests',
            headline: "Top 5 failed test cases by job",
            noDataReason: "uploaded test results",
            widgets: [timespanSelector.widget, description.widget]
        });
    var sunburst = zoomableSunburst(graph.svg, graphFactory.size);

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);
        graph.loading();

        dataSource.load('testcases?from='+ fromTimestamp, function (failures) {
            graph.loaded();

            var data = {
                name: "Most frequently failing tests",
                id: '__most_frequently_failing_tests__',
                children: transformFailingTests(failures)
            };

            sunburst.render(data);
        });
    });

}(timespanSelection, graphDescription, graphFactory, zoomableSunburst, dataSource, jobColors));
