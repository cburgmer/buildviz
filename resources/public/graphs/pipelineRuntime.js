(function (timespanSelection, graphFactory, runtimes, jobColors, dataSource) {
    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoMonths),
        graph = graphFactory.create({
            id: 'pipelineRuntime',
            headline: "Pipeline runtime",
            description: "<h3>When are we getting final feedback on changes?</h3><i>Color: final job of pipeline</i>",
            csvUrl: "/pipelineruntime.csv",
            noDataReason: "provided <code>start</code>, <code>end</code> times and <code>triggeredBy</code> information for your builds over at least two consecutive days",
            widgets: [timespanSelector.widget]
        });

    var transformRuntimes = function (data) {
        var pipelineEndJobNames = data.map(function (entry) {
            return entry.pipeline[entry.pipeline.length - 1];
        });
        var color = jobColors.colors(pipelineEndJobNames);

        return data.map(function (entry) {
            return {
                title: entry.pipeline.join(', '),
                color: color(entry.pipeline[entry.pipeline.length - 1]),
                runtimes: entry.runtimes.map(function (day) {
                    return {
                        date: new Date(day.date),
                        runtime: day.runtime / 1000
                    };
                })
            };
        }).filter(function (entry) {
            return entry.runtimes.length > 1;
        });
    };

    var runtimePane = runtimes(graph.svg);

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.load('/pipelineruntime?from=' + fromTimestamp, function (data) {
            graph.loaded();

            runtimePane.render(transformRuntimes(data));
        });
    });
}(timespanSelection, graphFactory, runtimes, jobColors, dataSource));
