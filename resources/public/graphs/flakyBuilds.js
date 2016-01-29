(function (timespanSelection, graphDescription, graphFactory, dataSource, badJobs) {
    var jobCount = 5,
        worstFlakyRatio = 1/4; /* one out of four */

    var flakyRatio = function (job) {
        return job.flakyCount / job.failedCount;
    };

    var flakyBuildsAsBubbles = function (jobEntries) {
        return jobEntries
            .filter(function (job) {
                return job.flakyCount > 0;
            })
            .map(function (job) {
                var ratio = flakyRatio(job);
                return {
                    name: job.jobName,
                    title: [job.jobName,
                            '',
                            job.failedCount + ' failures',
                            job.flakyCount + ' flaky failures',
                            (ratio * 100).toFixed(0) + '% of the time'].join('\n'),
                    ratio: ratio,
                    value: job.flakyCount
                };
            });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoWeeks),
        description = graphDescription.create({
            description: "The 5 jobs with the most flaky failing builds. A failing build is considered flaky, if another build that was given the same inputs passes. The size of each job's circle follows its total flaky build count. The border color shows the rate of flaky failures, calculated by total flaky build failures relative to total failing build count. This graph will prefer jobs with many flaky failures over jobs with a high rate of flaky failures.",
            answer: ["Where are implicit dependencies not made obvious?",
                     "Which jobs will probably be trusted the least?"],
            legend: "Border color: flaky ratio (no. flaky failures / no. failures), inner color: job, diameter: count of flaky builds",
            csvSource: "jobs.csv"
        }),
        graph = graphFactory.create({
            id: 'flakyBuilds',
            headline: "Top " + jobCount + " flaky jobs",
            noDataReason: "provided the <code>outcome</code> and <code>inputs</code> for relevant builds",
            widgets: [timespanSelector.widget, description.widget]
        });

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.load('jobs?from=' + fromTimestamp, function (data) {
            graph.loaded();

            var flakyJobs = flakyBuildsAsBubbles(data);

            badJobs.renderData(flakyJobs, graph.svg, jobCount, worstFlakyRatio);
        });
    });
}(timespanSelection, graphDescription, graphFactory, dataSource, badJobs));
