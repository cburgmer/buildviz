(function (timespanSelection, graphFactory, runtimes, jobColors, dataSource) {
    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoMonths),
        graph = graphFactory.create({
            id: 'jobRuntime',
            headline: "Job runtime",
            description: "<h3>Is the pipeline getting faster? Has a job gotten considerably slower?</h3><i>Color: job</i>",
            csvUrl: "/jobruntime.csv",
            noDataReason: "provided <code>start</code> and <code>end</code> times for your builds over at least two consecutive days",
            widgets: [timespanSelector.widget]
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
                runtimes: data
                    .map(function (d) {
                        return {
                            date: d.date,
                            runtime: d[jobName] ? (new Number(d[jobName]) * 24 * 60 * 60) : undefined
                        };
                    }).filter(function (d) {
                        return d.runtime !== undefined;
                    })
            };
        }).filter(function (jobRuntimes) {
            return jobRuntimes.runtimes.length > 1;
        });
    };

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.loadCSV('/jobruntime?from=' + fromTimestamp, function (data) {
            graph.loaded();

            runtimes.renderData(transformRuntimes(data), graph.svg);
        });
    });
}(timespanSelection, graphFactory, runtimes, jobColors, dataSource));
