(function (
    timespanSelection,
    graphDescription,
    jobColors,
    graphFactory,
    dataSource,
    weightedTimeline
) {
    const transformEntry = function (data, svg) {
        const jobNames = d3
            .set(
                data.map(function (test) {
                    return test.job;
                })
            )
            .values();
        const jobColor = jobColors.colors(jobNames);

        const flakyTests = data.map(function (testCase) {
            return {
                id: [testCase.job, testCase.classname, testCase.name].join(
                    "\\"
                ),
                name: testCase.name,
                color: jobColor(testCase.job),
                job: testCase.job,
                tooltip: [
                    testCase.name,
                    testCase.classname,
                    "",
                    testCase.job + " (" + testCase.testsuite + ")",
                    "Latest flaky build: " + testCase.latestBuildId,
                    "",
                    "Flaky count: " + testCase.flakyCount,
                    "Last flaky failure: " + testCase.latestFailure,
                ].join("<br>"),
                value: parseInt(testCase.flakyCount, 10),
                date: new Date(testCase.latestFailure.replace(" ", "T")), // HACK
            };
        });

        const isEqualDates = function (dateA, dateB) {
            return dateA.getTime() === dateB.getTime();
        };

        flakyTests.sort(function (a, b) {
            if (isEqualDates(a.date, b.date)) {
                return d3.descending(a.id, b.id);
            }
            return d3.descending(a.date, b.date);
        });

        return flakyTests;
    };

    const timespanSelector = timespanSelection.create(
            timespanSelection.timespans.twoWeeks
        ),
        description = graphDescription.create({
            description: [
                "All flaky test cases.",
                "A test case is considered flaky if it failed in one build, but passed in another,",
                "given that both builds were run with the same inputs.",
                "Multiple test cases with the same name have their flaky failure counts added up.",
            ].join(" "),
            answer: [
                "Which tests provide questionable value and will probably be trusted the least?",
            ],
            legend: "",
            csvSource: "flakytestcases.csv",
        }),
        graph = graphFactory.create({
            id: "flakyTests",
            headline: "Flaky tests cases",
            noDataReason:
                "provided the <code>inputs</code> for relevant builds and uploaded test results",
            widgets: [timespanSelector.widget, description.widget],
        });

    const runtimePane = weightedTimeline(graph.svg, "Last seen");

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.loadCSV(
            "flakytestcases?from=" + fromTimestamp,
            function (data) {
                graph.loaded();

                runtimePane.render(transformEntry(data), fromTimestamp);
            }
        );
    });
})(
    timespanSelection,
    graphDescription,
    jobColors,
    graphFactory,
    dataSource,
    weightedTimeline
);
