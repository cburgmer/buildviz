(function (timespanSelection, graphDescription, graphFactory, durationsByDay, jobColors, dataSource) {
    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoMonths),
        description = graphDescription.create({
            description: "Job runtime over time, average by day. The job's runtime is calculated as time between start and end of its builds.",
            answer: ['Has a job gotten considerably slower?',
                     'Are optimizations showing?',
                     'Has a job gotten suspiciously fast?'],
            legend: 'Color: job',
            csvSource: "jobruntime.csv"

        }),
        graph = graphFactory.create({
            id: 'jobRuntime',
            headline: "Job runtime",
            noDataReason: "provided <code>start</code> and <code>end</code> times for your builds over at least two consecutive days",
            widgets: [timespanSelector.widget, description.widget]
        });

    var transformRuntimes = function (jobRuntimes) {
        var jobNames = jobRuntimes.map(function (jobEntry) {
            return jobEntry.job;
        });
        var color = jobColors.colors(jobNames);

        return jobRuntimes.map(function (jobEntry) {
            return {
                title: jobEntry.job,
                color: color(jobEntry.job),
                durations: jobEntry.runtimes.map(function (runtimeEntry) {
                    return {
                        date: new Date(runtimeEntry.date),
                        duration: runtimeEntry.runtime / 1000
                    };
                })
            };
        }).filter(function (jobRuntimes) {
            return jobRuntimes.durations.length > 1;
        });
    };

    var runtimePane = durationsByDay(graph.svg, 'Average runtime');

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.load('jobruntime?from=' + fromTimestamp, function (data) {
            graph.loaded();

            runtimePane.render(transformRuntimes(data), fromTimestamp);
        });
    });
}(timespanSelection, graphDescription, graphFactory, durationsByDay, jobColors, dataSource));
