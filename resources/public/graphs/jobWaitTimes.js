(function (
    timespanSelection,
    graphDescription,
    graphFactory,
    events,
    jobColors,
    dataSource
) {
    const timespanSelector = timespanSelection.create(
            timespanSelection.timespans.twoMonths
        ),
        description = graphDescription.create({
            description: [
                "Average job wait times by day.",
                "Wait times are calculated from the delay between the build starting and the end of the previous triggering build.",
                "This delay may be caused by the job itself already running from a previous build consequently blocking a new execution,",
                "manual execution, or build scheduler congestion.",
                "This graph can help find bottlenecks in a build pipeline,",
                "and show where changes are just sitting idly, waiting to be verified.",
            ].join(" "),
            answer: [
                "Where is time wasted in the pipeline?",
                "Where are multiple changes possibly queuing up for processing?",
            ],
            legend: "Color: job",
            csvSource: "waittimes.csv",
        }),
        graph = graphFactory.create({
            id: "waitTimes",
            headline: "Job wait times",
            noDataReason:
                "provided <code>start</code>, <code>end</code> times and <code>triggeredBy</code> information for your builds",
            widgets: [timespanSelector.widget, description.widget],
        });

    const eventForBuildWaitTime = function (buildWaitTime, color) {
        return {
            date: new Date(buildWaitTime.start),
            value: buildWaitTime.waitTime,
            color: color,
            tooltip:
                "<div>" +
                buildWaitTime.job +
                " #" +
                buildWaitTime.buildId +
                "</div>" +
                "<div>" +
                utils.formatTimeInMs(buildWaitTime.waitTime) +
                "</div>" +
                "<div>triggered by " +
                buildWaitTime.triggeredBy.job +
                " #" +
                buildWaitTime.triggeredBy.buildId +
                "</div>",
        };
    };

    const transformWaitTimes = function (data) {
        const waitTimesByJob = d3
            .nest()
            .key(function (d) {
                return d.job;
            })
            .sortValues(function (a, b) {
                return b.start - a.start;
            })
            .entries(data);

        const jobNames = waitTimesByJob.map(function (group) {
            return group.key;
        });
        const color = jobColors.colors(jobNames);

        return waitTimesByJob.map(function (group) {
            const c = color(group.key);
            return {
                id: group.key,
                color: c,
                events: group.values.map(function (entry) {
                    return eventForBuildWaitTime(entry, c);
                }),
            };
        });
    };

    const waitTimesPane = events(graph.svg, "Average wait time");

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.load("waittimes?from=" + fromTimestamp, function (data) {
            graph.loaded();

            waitTimesPane.render(transformWaitTimes(data), fromTimestamp);
        });
    });
})(
    timespanSelection,
    graphDescription,
    graphFactory,
    events,
    jobColors,
    dataSource
);
