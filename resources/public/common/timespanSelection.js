var timespanSelection = function (d3) {
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
            label: 'all time',
            timestamp: function () { return 0; }
        },
        twoWeeks: {
            label: 'last two weeks',
            timestamp: from2WeeksAgo
        },
        sevenDays: {
            label: 'last 7 days',
            timestamp: from7DaysAgo
        },
        today: {
            label: 'today',
            timestamp: startOfToday
        }
    };

    module.startingFromTimestamp = function (span) {
        return span.timestamp.call();
    };

    module.create = function (selectedSpan) {
        var onTimespanSelected;

        var container = d3.select(document.createElement('div'))
                .attr('class', 'timespan');

        var currentlySelected = container.append('span')
            .text(selectedSpan.label);

        var timespanList = container.append('div')
                .attr('class', 'timespanSelection')
                .text('Aggregate over data from')
                .append('ol')
                .attr('class', 'timespanList');

        Object.keys(module.timespans).forEach(function (spanName) {
            var span = module.timespans[spanName];

            timespanList.append('li')
                .attr('class', 'item')
                .append("button")
                .text(span.label)
                .on('click', function () {
                    currentlySelected.text(span.label);
                    onTimespanSelected(span);
                    d3.event.preventDefault();
                });
        });

        return {
            widget: container.node(),
            load: function (onTimespanSelectedFunction) {
                onTimespanSelected = onTimespanSelectedFunction;

                onTimespanSelected(selectedSpan);
            }
        };
    };

    return module;
}(d3);
