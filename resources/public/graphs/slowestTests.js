(function(
    timespanSelection,
    graphDescription,
    graphFactory,
    zoomableSunburst,
    dataSource,
    jobColors,
    utils
) {
    const testCountPerJob = 5;

    const concatIds = function(ids) {
        return ids.join("/");
    };

    const title = function(entry) {
        return [
            entry.name +
                " (" +
                utils.formatTimeInMs(entry.averageRuntime, {
                    showMillis: true
                }) +
                ")",
            entry.testClass,
            entry.testSuite
        ].join("\n");
    };

    const transformTestCase = function(testCase, jobName) {
        return {
            name: testCase.name,
            size: testCase.averageRuntime,
            title: title(testCase),
            jobName: jobName,
            id: concatIds([
                jobName,
                testCase.testSuite,
                testCase.testClass,
                testCase.name
            ])
        };
    };

    const flattenTests = function(testsuites) {
        return testsuites.reduce(function(tests, testsuite) {
            const suiteTests = testsuite.children.reduce(function(
                tests,
                testClass
            ) {
                return tests.concat(
                    testClass.children.map(function(test) {
                        return {
                            name: test.name,
                            averageRuntime: test.averageRuntime,
                            testSuite: testsuite.name,
                            testClass: testClass.name
                        };
                    })
                );
            },
            []);

            return tests.concat(suiteTests);
        }, []);
    };

    const slowestNTests = function(testsuites, n) {
        const tests = flattenTests(testsuites);

        tests.sort(function(a, b) {
            return a.averageRuntime - b.averageRuntime;
        });

        const slowestTests = tests.slice(-n),
            remaindingTotalAverageRuntime = tests
                .slice(0, -n)
                .reduce(function(totalAverageRuntime, test) {
                    return totalAverageRuntime + test.averageRuntime;
                }, 0);

        if (remaindingTotalAverageRuntime) {
            slowestTests.push({
                name: "Remaining average",
                averageRuntime:
                    remaindingTotalAverageRuntime / (tests.length - n),
                remainder: true
            });
        }

        return slowestTests;
    };

    const transformTestCases = function(testcasesByJob) {
        const jobNames = testcasesByJob.map(function(jobEntry) {
            return jobEntry.jobName;
        });
        const color = jobColors.colors(jobNames);

        return testcasesByJob.map(function(jobEntry) {
            const jobName = jobEntry.jobName,
                children = slowestNTests(jobEntry.children, testCountPerJob);

            return {
                name: jobName,
                color: color(jobName),
                id: "jobname-" + jobName,
                jobName: jobName,
                children: children.map(function(child) {
                    return transformTestCase(child, jobName);
                })
            };
        });
    };

    const timespanSelector = timespanSelection.create(
            timespanSelection.timespans.sevenDays
        ),
        description = graphDescription.create({
            description:
                "Average runtime of the 5 slowest test cases by job. Multiple test cases with the same name have their runtimes added up.",
            answer: [
                "What could be the first place to look at to improve test runtime?"
            ],
            legend:
                "Color: job/test suite, arc size: avarage test case runtime",
            csvSource: "testcases.csv"
        }),
        graph = graphFactory.create({
            id: "slowestTests",
            headline: "Top 5 slowest test cases by job",
            noDataReason: "uploaded test results",
            widgets: [timespanSelector.widget, description.widget]
        });
    const sunburst = zoomableSunburst(graph.svg, graphFactory.size);

    timespanSelector.load(function(fromTimestamp) {
        graph.loading();

        dataSource.load("testcases?from=" + fromTimestamp, function(testCases) {
            graph.loaded();

            const data = {
                name: "Tests",
                id: "__tests__",
                color: "transparent",
                children: transformTestCases(testCases)
            };

            sunburst.render(data);
        });
    });
})(
    timespanSelection,
    graphDescription,
    graphFactory,
    zoomableSunburst,
    dataSource,
    jobColors,
    utils
);
