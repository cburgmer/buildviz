(function (timespanSelection, graphFactory, runtimes, dataSource) {
    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoMonths),
        graph = graphFactory.create({
            id: 'pipelineRuntime',
            headline: "Pipeline runtime",
            description: "<h3>When are we getting final feedback on changes?</h3>",
            csvUrl: "/pipelineruntime.csv",
            noDataReason: "provided <code>start</code>, <code>end</code> times and <code>triggeredBy</code> information for your builds over at least two consecutive days",
            widgets: [timespanSelector.widget]
        });

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.loadCSV('/pipelineruntime?from=' + fromTimestamp, function (data) {
            graph.loaded();

            runtimes.renderData(data, graph.svg);
        });
    });
}(timespanSelection, graphFactory, runtimes, dataSource));
