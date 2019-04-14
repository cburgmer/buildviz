(function(
    timespanSelection,
    graphDescription,
    graphFactory,
    dataSource,
    badJobs
) {
    const jobCount = 5,
        worstFailureRatio = 0.25;

    const failRatio = function(job) {
        return job.failedCount / job.totalCount;
    };

    const failedBuildsAsBubbles = function(jobEntries) {
        return jobEntries
            .filter(function(job) {
                return job.failedCount > 0;
            })
            .map(function(job) {
                const ratio = failRatio(job);
                return {
                    name: job.jobName,
                    title: [
                        job.jobName,
                        "",
                        job.totalCount + " runs",
                        job.failedCount + " failures",
                        (ratio * 100).toFixed(0) + "% of the time"
                    ].join("\n"),
                    ratio: ratio,
                    value: job.failedCount
                };
            });
    };

    const timespanSelector = timespanSelection.create(
            timespanSelection.timespans.twoWeeks
        ),
        description = graphDescription.create({
            description: [
                "The 5 jobs with the most failed builds. The size of each job's circle follows its total failed build count.",
                "The border color shows the failure rate, calculated by total build failures relative to total build count.",
                "This graph will prefer jobs with many failures over jobs with a high failure rate."
            ].join(" "),
            answer: [
                "What needs most manual intervention?",
                "Where are the biggest quality issues?",
                "Where do we receive either not so valuable or actually very valuable feedback?"
            ],
            legend:
                "Border color: failure ratio (no. failures / no. runs), inner color: job, diameter: number of failures",
            csvSource: "jobs.csv"
        }),
        graph = graphFactory.create({
            id: "failedBuilds",
            headline: "Top 5 failed jobs",
            noDataReason: "provided the <code>outcome</code> of your builds",
            widgets: [timespanSelector.widget, description.widget]
        });

    timespanSelector.load(function(fromTimestamp) {
        graph.loading();

        dataSource.load("jobs?from=" + fromTimestamp, function(data) {
            graph.loaded();

            const failedJobs = failedBuildsAsBubbles(data);

            badJobs.renderData(
                failedJobs,
                graph.svg,
                jobCount,
                worstFailureRatio
            );
        });
    });
})(timespanSelection, graphDescription, graphFactory, dataSource, badJobs);
