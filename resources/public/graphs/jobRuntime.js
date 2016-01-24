(function (timespanSelection, graphDescription, graphFactory, durationsByDay, jobColors, dataSource) {
    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoMonths),
        description = graphDescription.create({
            description: "Job runtime over time, average by day. The job's runtime is calculated as time between start and end of its builds.",
            answer: ['Has a job gotten considerably slower?',
                     'Are optimizations showing?',
                     'Has a job gotten suspiciously fast?'],
            legend: 'Color: job',
            csvSource: "/jobruntime.csv"

        }),
        graph = graphFactory.create({
            id: 'jobRuntime',
            headline: "Job runtime",
            noDataReason: "provided <code>start</code> and <code>end</code> times for your builds over at least two consecutive days",
            widgets: [timespanSelector.widget, description.widget]
        });

    var transformRuntimes = function (data) {
        var jobNames = d3.keys(data[0]).filter(function(key) { return key !== "date"; }),
            color = jobColors.colors(jobNames);

        data.forEach(function (d) {
            d.date = new Date(d.date);
        });

        return jobNames.map(function (jobName) {
            return {
                title: jobName,
                color: color(jobName),
                durations: data
                    .map(function (d) {
                        return {
                            date: d.date,
                            duration: d[jobName] ? (new Number(d[jobName]) * 24 * 60 * 60) : undefined
                        };
                    }).filter(function (d) {
                        return d.duration !== undefined;
                    })
            };
        }).filter(function (jobRuntimes) {
            return jobRuntimes.durations.length > 1;
        });
    };

    var runtimePane = durationsByDay(graph.svg, 'Average runtime');

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.loadCSV('/jobruntime?from=' + fromTimestamp, function (data) {
            graph.loaded();

            runtimePane.render(transformRuntimes(data));
        });
    });
}(timespanSelection, graphDescription, graphFactory, durationsByDay, jobColors, dataSource));
