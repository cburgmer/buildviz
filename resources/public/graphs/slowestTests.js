(function (timespanSelection, graphDescription, graphFactory, zoomableSunburst, dataSource, jobColors, utils) {
    var testCountPerJob = 5;

    var concatIds = function (ids) {
        return ids.join('/');
    };

    var title = function (entry) {
        return [entry.name + ' (' + utils.formatTimeInMs(entry.averageRuntime, {showMillis: true}) + ')',
                entry.testClass,
                entry.testSuite].join('\n');
    };

    var transformTestCase = function (testCase, parentId) {
        return {
            name: testCase.name,
            size: testCase.averageRuntime,
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
                        averageRuntime: test.averageRuntime,
                        testSuite: testsuite.name,
                        testClass: testClass.name
                    };
                }));
            }, []);

            return tests.concat(suiteTests);
        }, []);
    };

    var slowestNTests = function (testsuites, n) {
        var tests = flattenTests(testsuites);

        tests.sort(function (a, b) {
            return a.averageRuntime - b.averageRuntime;
        });

        var slowestTests = tests.slice(-n),
            remaindingTotalAverageRuntime = tests.slice(0, -n).reduce(function (totalAverageRuntime, test) {
                return totalAverageRuntime + test.averageRuntime;
            }, 0);

        if (remaindingTotalAverageRuntime) {
            slowestTests.push({
                name: 'Remaining average',
                averageRuntime: remaindingTotalAverageRuntime / (tests.length - n),
                remainder: true
            });
        }

        return slowestTests;
    };

    var transformTestCases = function (testcasesByJob) {
        var jobNames = testcasesByJob.map(function (jobEntry) {
            return jobEntry.jobName;
        });
        var color = jobColors.colors(jobNames);

        return testcasesByJob.map(function (jobEntry) {
            var jobName = jobEntry.jobName,
                children = slowestNTests(jobEntry.children, testCountPerJob);

            return {
                name: jobName,
                color: color(jobName),
                id: 'jobname-' + jobName,
                children: children.map(function (child) {
                    return transformTestCase(child, jobName);
                })
            };
        });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.sevenDays),
        description = graphDescription.create({
            description: 'Average runtime of the 5 slowest test cases by job. Multiple test cases with the same name have their runtimes added up.',
            answer: ['What could be the first place to look at to improve test runtime?'],
            legend: 'Color: job/test suite, arc size: avarage test case runtime',
            csvSource: "testcases.csv"
        }),
        graph = graphFactory.create({
            id: 'slowestTests',
            headline: "Top 5 slowest test cases by job",
            noDataReason: "uploaded test results",
            widgets: [timespanSelector.widget, description.widget]
        });
    var sunburst = zoomableSunburst(graph.svg, graphFactory.size);

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.load('testcases?from='+ fromTimestamp, function (testCases) {
            graph.loaded();

            var data = {
                name: "Tests",
                id: '__tests__',
                color: 'transparent',
                children: transformTestCases(testCases)
            };

            sunburst.render(data);
        });
    });

}(timespanSelection, graphDescription, graphFactory, zoomableSunburst, dataSource, jobColors, utils));
