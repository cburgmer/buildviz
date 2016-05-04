(function (timespanSelection, graphDescription, graphFactory, durationsByDay, jobColors, dataSource) {
    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoMonths),
        description = graphDescription.create({
            description: "Runtime over time for all pipelines identified for the given interval, average by day. A pipeline is considered a simple chain of jobs, each triggering another until the pipeline finishes. The time between the start of the first build and the end of the last build makes up the runtime of a pipeline run.",
            answer: ["When are we getting final feedback on changes?"],
            legend: "Color: final job of pipeline",
            csvSource: "pipelineruntime.csv"
        }),
        graph = graphFactory.create({
            id: 'pipelineRuntime',
            headline: "Pipeline runtime",
            noDataReason: "provided <code>start</code>, <code>end</code> times and <code>triggeredBy</code> information for your builds over at least two consecutive days",
            widgets: [timespanSelector.widget, description.widget]
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
                durations: entry.runtimes.map(function (day) {
                    return {
                        date: new Date(day.date),
                        duration: day.runtime / 1000
                    };
                })
            };
        }).filter(function (entry) {
            return entry.durations.length > 1;
        });
    };

    var runtimePane = durationsByDay(graph.svg, 'Average runtime');

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.load('pipelineruntime?from=' + fromTimestamp, function (data) {
            graph.loaded();

            runtimePane.render(transformRuntimes(data));
        });
    });
}(timespanSelection, graphDescription, graphFactory, durationsByDay, jobColors, dataSource));
