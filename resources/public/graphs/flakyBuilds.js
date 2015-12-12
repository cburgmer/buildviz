(function (timespanSelection, graphFactory, dataSource, badJobs) {
    var jobCount = 5,
        worstFlakyRatio = 0.10;

    var flakyRatio = function (job) {
        var flakyCount = job.flakyCount || 0;
        return flakyCount / job.totalCount;
    };

    var flakyBuildsAsBubbles = function (jobEntries) {
        return jobEntries
            .filter(function (job) {
                return job.flakyCount > 0;
            })
            .map(function (job) {
                var flakyCount = job.flakyCount,
                    ratio = flakyRatio(job);
                return {
                    name: job.jobName,
                    title: job.jobName + '\n\n' + flakyCount + ' flaky failures\n' + (ratio * 100).toFixed(0) + '% of the time',
                    ratio: ratio,
                    value: flakyCount
                };
            });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoWeeks),
        graph = graphFactory.create({
            id: 'flakyBuilds',
            headline: "Top " + jobCount + " flaky builds",
            description: "<h3>Where are implicit dependencies not made obvious? Which jobs will probably be trusted the least?</h3><i>Border color: flaky ratio, inner color: job, diameter: flaky count</i>",
            csvUrl: "/jobs.csv",
            noDataReason: "provided the <code>outcome</code> and <code>inputs</code> for relevant builds",
            widgets: [timespanSelector.widget]
        });

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.load('/jobs?from=' + fromTimestamp, function (data) {
            graph.loaded();

            var flakyJobs = flakyBuildsAsBubbles(data);

            badJobs.renderData(flakyJobs, graph.svg, jobCount, worstFlakyRatio);
        });
    });
}(timespanSelection, graphFactory, dataSource, badJobs));
