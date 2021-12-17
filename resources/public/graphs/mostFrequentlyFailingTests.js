(function (
    timespanSelection,
    graphDescription,
    graphFactory,
    zoomableSunburst,
    dataSource,
    jobColors
) {
    const testCountPerJob = 5;

    const concatIds = function (ids) {
        return ids.join("/");
    };

    const title = function (entry) {
        const failures = entry.failedCount
            ? " (" + entry.failedCount + ")"
            : "";
        return [entry.name + failures, entry.testClass, entry.testSuite].join(
            "\n"
        );
    };

    const transformTestCase = function (testCase, jobName) {
        return {
            name: testCase.name,
            size: testCase.failedCount,
            title: title(testCase),
            jobName: jobName,
            id: concatIds([
                jobName,
                testCase.testSuite,
                testCase.testClass,
                testCase.name,
            ]),
        };
    };

    const flattenTests = function (testsuites) {
        return testsuites.reduce(function (tests, testsuite) {
            const suiteTests = testsuite.children.reduce(function (
                tests,
                testClass
            ) {
                return tests.concat(
                    testClass.children.map(function (test) {
                        return {
                            name: test.name,
                            failedCount: test.failedCount,
                            testSuite: testsuite.name,
                            testClass: testClass.name,
                        };
                    })
                );
            },
            []);

            return tests.concat(suiteTests);
        }, []);
    };

    const excludeAlwaysGreenTests = function (tests) {
        return tests.filter(function (testCase) {
            return testCase.failedCount > 0;
        });
    };

    const mostNFailingTestCases = function (testsuites, n) {
        const tests = excludeAlwaysGreenTests(flattenTests(testsuites));

        tests.sort(function (a, b) {
            return a.failedCount - b.failedCount;
        });

        const mostFailingTests = tests.slice(-n),
            remaindingTotalFailedCount = tests
                .slice(0, -n)
                .reduce(function (totalFailedCount, test) {
                    return totalFailedCount + test.failedCount;
                }, 0);

        if (remaindingTotalFailedCount) {
            mostFailingTests.push({
                name: "Remaining average",
                failedCount: remaindingTotalFailedCount / (tests.length - n),
                remainder: true,
            });
        }

        return mostFailingTests;
    };

    const transformFailingTests = function (testcasesByJob) {
        const jobNames = testcasesByJob.map(function (jobEntry) {
            return jobEntry.jobName;
        });
        const color = jobColors.colors(jobNames);

        return testcasesByJob
            .map(function (jobEntry) {
                const jobName = jobEntry.jobName,
                    children = mostNFailingTestCases(
                        jobEntry.children,
                        testCountPerJob
                    );

                return {
                    name: jobName,
                    color: color(jobName),
                    id: "jobname-" + jobName,
                    jobName: jobName,
                    children: children.map(function (child) {
                        return transformTestCase(child, jobName);
                    }),
                };
            })
            .filter(function (entry) {
                return entry.children.length > 0;
            });
    };

    const timespanSelector = timespanSelection.create(
            timespanSelection.timespans.sevenDays
        ),
        description = graphDescription.create({
            description:
                "The 5 test cases with the most failures by job. Multiple test cases with the same name have their failures added up.",
            answer: [
                "What are the tests that provide either the most or the least feedback?",
            ],
            legend: "Color: job/test suite, arc size: number of test failures",
            csvSource: "testcases.csv",
        }),
        graph = graphFactory.create({
            id: "mostFrequentlyFailingTests",
            headline: "Top 5 failed test cases by job",
            noDataReason: "uploaded test results",
            widgets: [timespanSelector.widget, description.widget],
        });
    const sunburst = zoomableSunburst(graph.svg, graphFactory.size);

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.load("testcases?from=" + fromTimestamp, function (failures) {
            graph.loaded();

            const data = {
                name: "Most frequently failing tests",
                id: "__most_frequently_failing_tests__",
                color: "transparent",
                children: transformFailingTests(failures),
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
    jobColors
);
