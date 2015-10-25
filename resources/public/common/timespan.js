var timespan = function (d3) {
    var module = {};

    var startOfToday = function () {
        var today = new Date(),
            twoWeeksAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
        return +twoWeeksAgo;
    };

    var from7DaysAgo = function () {
        var today = new Date(),
            twoWeeksAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 7);
        return +twoWeeksAgo;
    };
    var from2WeeksAgo = function () {
        var today = new Date(),
            twoWeeksAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 14);
        return +twoWeeksAgo;
    };

    module.timespans = {
        all: {
            label: 'All',
            timestamp: function () { return 0; }
        },
        twoWeeks: {
            label: 'Last two weeks',
            timestamp: from2WeeksAgo
        },
        sevenDays: {
            label: 'Last 7 days',
            timestamp: from7DaysAgo
        },
        today: {
            label: 'Today',
            timestamp: startOfToday
        }
    };

    module.startingFromTimestamp = function (span) {
        return span.timestamp.call();
    };

    module.createSelector = function (selectedSpan, onTimespanSelected) {
        var container = d3.select(document.createElement('div'));

        Object.keys(module.timespans).forEach(function (spanName) {
            var span = module.timespans[spanName];

            container.append("button")
                .text(span.label)
                .on('click', function () {
                    onTimespanSelected(span);
                    d3.event.preventDefault();
                });
        });

        return container.node();
    };

    return module;
}(d3);
