(function (timespanSelection, graphDescription, graphFactory, durationsByDay, jobColors, dataSource) {
    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoMonths),
        description = graphDescription.create({
            description: "Average job wait times by day. Wait times are calculated from the delay between the build starting and the end of the previous triggering build. This delay may be caused by the job itself already running from a previous build consequently blocking a new execution, manual execution, or build scheduler congestion. This graph can help find bottlenecks in a build pipeline, and show where changes are just sitting idly, waiting to be verified.",
            answer: ["Where is time wasted in the pipeline?",
                     "Where are multiple changes possibly queuing up for processing?"],
            legend: "Color: job",
            csvSource: "waittimes.csv"
        }),
        graph = graphFactory.create({
            id: 'waitTimes',
            headline: "Job wait times",
            noDataReason: "provided <code>start</code>, <code>end</code> times and <code>triggeredBy</code> information for your builds over at least two consecutive days",
            widgets: [timespanSelector.widget, description.widget]
        });

    var transformWaitTimes = function (data) {
        var jobNames = data.map(function (entry) {
            return entry.job;
        });
        var color = jobColors.colors(jobNames);

        return data.map(function (entry) {
            return {
                title: entry.job,
                color: color(entry.job),
                durations: entry.waitTimes.map(function (day) {
                    return {
                        date: new Date(day.date),
                        duration: day.waitTime / 1000
                    };
                })
            };
        }).filter(function (entry) {
            return entry.durations.length > 1;
        });
    };

    var waitTimesPane = durationsByDay(graph.svg, 'Average wait time');

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.load('waittimes?from=' + fromTimestamp, function (data) {
            graph.loaded();

            waitTimesPane.render(transformWaitTimes(data));
        });
    });
}(timespanSelection, graphDescription, graphFactory, durationsByDay, jobColors, dataSource));
