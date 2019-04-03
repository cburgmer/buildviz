(function (timespanSelection, graphDescription, graphFactory, events, jobColors, dataSource, utils) {
    const timespanSelector = timespanSelection.create(timespanSelection.timespans.twoMonths),
        description = graphDescription.create({
            description: "Build runtime (time between start and end)",
            answer: ['Has a job gotten considerably slower?',
                     'Are optimizations showing?',
                     'Has a job gotten suspiciously fast?'],
            legend: 'Color: job',
            csvSource: "builds.csv"

        }),
        graph = graphFactory.create({
            id: 'builds',
            headline: "Builds",
            noDataReason: "provided <code>start</code> and <code>end</code> times for your builds",
            widgets: [timespanSelector.widget, description.widget]
        });

    const eventForBuild = function (build, color) {
        const isFailingBuild = build.outcome && build.outcome !== 'pass';
        const duration = (build.end - build.start);
        return {
            date: new Date(build.end),
            value: duration,
            color: color,
            highlight: isFailingBuild,
            tooltip: '<div>' + build.job + ' #' + build.buildId + '</div>' +
                '<div>' + utils.formatTimeInMs(duration) + '</div>' +
                (isFailingBuild ? '<div>failed</div>' : '')
        };
    };

    const transformBuilds = function (builds) {
        const buildsByJob = d3.nest()
                .key(function (d) {
                    return d.job;
                })
                .sortValues(function (a, b) { return b.end - a.end; })
                .entries(builds);

        const jobNames = buildsByJob.map(function (group) {
            return group.key;
        });
        const color = jobColors.colors(jobNames);

        return buildsByJob.map(function (group) {
            const c = color(group.key);
            const events = group.values
                    .filter(function (build) {
                        return build.end;
                    })
                    .map(function (build) {
                        return eventForBuild(build, c);
                    });
            return {
                id: group.key,
                color: c,
                events: events
            };
        });
    };

    const runtimePane = events(graph.svg, 'Runtime');

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.load('builds?from=' + fromTimestamp, function (data) {
            graph.loaded();

            runtimePane.render(transformBuilds(data), fromTimestamp);
        });
    });
}(timespanSelection, graphDescription, graphFactory, events, jobColors, dataSource, utils));
