(function (timespanSelection, graphFactory, dataSource, badJobs) {
    var jobCount = 5,
        worstFailureRatio = 0.25;

    var failRatio = function (job) {
        var failCount = job.failedCount || 0;
        return failCount / job.totalCount;
    };

    var failedBuildsAsBubbles = function (pipeline) {
        return Object.keys(pipeline)
            .filter(function (jobName) {
                return pipeline[jobName].failedCount > 0;
            })
            .map(function (jobName) {
                var failedCount = pipeline[jobName].failedCount,
                    ratio = failRatio(pipeline[jobName]);
                return {
                    name: jobName,
                    title: jobName + '\n\n' + failedCount + ' failures\n' + (ratio * 100).toFixed(0) + '% of the time',
                    ratio: ratio,
                    value: failedCount
                };
            });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoWeeks),
        graph = graphFactory.create({
            id: 'failedBuilds',
            headline: "Top 5 failed builds",
            description: "<h3>What needs most manual intervention? Where are the biggest quality issues? Where do we receive either not so valuable or actually very valuable feedback?</h3><i>Border color: failure ratio, inner color: job, diameter: number of failures</i>",
            csvUrl: "/jobs.csv",
            noDataReason: "provided the <code>outcome</code> of your builds",
            widgets: [timespanSelector.widget]
        });

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.load('/jobs?from=' + fromTimestamp, function (data) {
            graph.loaded();

            var failedJobs = failedBuildsAsBubbles(data);

            badJobs.renderData(failedJobs, graph.svg, jobCount, worstFailureRatio);
        });
    });

}(timespanSelection, graphFactory, dataSource, badJobs));
