(function (timespanSelection, graphFactory, dataSource, badJobs) {
    var jobCount = 5,
        worstFailureRatio = 0.25;

    var failRatio = function (job) {
        return job.failedCount / job.totalCount;
    };

    var failedBuildsAsBubbles = function (jobEntries) {
        return jobEntries
            .filter(function (job) {
                return job.failedCount > 0;
            })
            .map(function (job) {
                var ratio = failRatio(job);
                return {
                    name: job.jobName,
                    title: [job.jobName,
                            '',
                            job.totalCount + ' runs',
                            job.failedCount + ' failures',
                            (ratio * 100).toFixed(0) + '% of the time'].join('\n'),
                    ratio: ratio,
                    value: job.failedCount
                };
            });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoWeeks),
        graph = graphFactory.create({
            id: 'failedBuilds',
            headline: "Top 5 failed jobs",
            description: "<h3>What needs most manual intervention? Where are the biggest quality issues? Where do we receive either not so valuable or actually very valuable feedback?</h3><i>Border color: failure ratio (no. failures / no. runs), inner color: job, diameter: number of failures</i>",
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
