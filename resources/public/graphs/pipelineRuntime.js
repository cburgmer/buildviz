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
                "Runtime over time for all pipelines identified for the given interval, average by day.",
                "A pipeline is considered a simple chain of jobs, each triggering another until the pipeline finishes.",
                "The time between the start of the first build and the end of the last build makes up the runtime of a pipeline run.",
            ].join(" "),
            answer: ["When are we getting final feedback on changes?"],
            legend: "Color: final job of pipeline",
            csvSource: "pipelineruntime.csv",
        }),
        graph = graphFactory.create({
            id: "pipelineRuntime",
            headline: "Pipeline runtime",
            noDataReason:
                "provided <code>start</code>, <code>end</code> times and <code>triggeredBy</code> information for your builds",
            widgets: [timespanSelector.widget, description.widget],
        });

    const transformRuntimes = function (data) {
        const pipelineRuntimesByPipeline = d3
            .nest()
            .key(function (d) {
                return d.pipeline.join("/");
            })
            .sortValues(function (a, b) {
                return b.start - a.start;
            })
            .entries(data);

        const pipelineEndJobNames = pipelineRuntimesByPipeline.map(function (
            group
        ) {
            return group.key[group.key.length - 1];
        });
        const color = jobColors.colors(pipelineEndJobNames);

        return pipelineRuntimesByPipeline.map(function (group) {
            const pipeline = group.values[0].pipeline;
            const c = color(pipeline[pipeline.length - 1]);
            return {
                id: group.key,
                color: c,
                tooltip: pipeline.join("<br>→ "),
                events: group.values.map(function (pipelineRun) {
                    const duration = pipelineRun.end - pipelineRun.start;
                    const buildNames = pipelineRun.builds.map(function (build) {
                        return build.job + " #" + build.buildId;
                    });
                    return {
                        date: new Date(pipelineRun.end),
                        value: duration,
                        color: c,
                        tooltip:
                            "<div>" +
                            utils.formatTimeInMs(duration) +
                            "</div>" +
                            buildNames.join("<br>→ "),
                    };
                }),
            };
        });
    };

    const runtimePane = events(graph.svg, "Average runtime", "lines");

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.load(
            "pipelineruntime?from=" + fromTimestamp,
            function (data) {
                graph.loaded();

                runtimePane.render(transformRuntimes(data), fromTimestamp);
            }
        );
    });
})(
    timespanSelection,
    graphDescription,
    graphFactory,
    events,
    jobColors,
    dataSource
);
