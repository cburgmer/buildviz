var timespanSelection = function (d3) {
    var module = {};

    var fromDaysAgo = function (days) {
        return function () {
            var today = new Date(),
                daysAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - days);

            return +daysAgo;
        };
    };

    module.timespans = {
        all: {
            label: 'all time',
            shortLabel: 'all time',
            timestamp: function () { return 0; }
        },
        twoMonths: {
            label: 'last two months',
            shortLabel: '2 months',
            timestamp: fromDaysAgo(60)
        },
        twoWeeks: {
            label: 'last two weeks',
            shortLabel: '2 weeks',
            timestamp: fromDaysAgo(14)
        },
        sevenDays: {
            label: 'last 7 days',
            shortLabel: '7 days',
            timestamp: fromDaysAgo(7)
        },
        twoDays: {
            label: 'last 2 days',
            shortLabel: '2 days',
            timestamp: fromDaysAgo(2)
        },
        today: {
            label: 'today',
            shortLabel: 'today',
            timestamp: fromDaysAgo(1)
        }
    };

    var notifyTimespanSelected = function (onTimespanSelected, span) {
        onTimespanSelected(span.timestamp.call());
    };

    module.create = function (selectedSpan) {
        var onTimespanSelected;

        var container = d3.select(document.createElement('div'))
                .attr('class', 'timespan');

        var currentlySelected = container.append('span')
                .on('click', function () {
                    d3.event.preventDefault();
                });

        var timespanList = container.append('div')
                .attr('class', 'timespanSelection')
                .append('ol')
                .attr('class', 'timespanList');

        var timespans = Object.keys(module.timespans).map(function (spanName) {
            return module.timespans[spanName];
        });

        var selection = timespanList.selectAll(".item")
                .data(timespans,
                      function (d) {
                          return d.label;
                      });

        var updateSelection = function () {
            currentlySelected.text(selectedSpan.shortLabel);
            selection
                .classed('selected', function (span) {
                    return span === selectedSpan;
                });
        };

        selection.enter()
            .append('li')
            .attr('class', 'item')
            .append("button")
            .text(function (span) {
                return span.label;
            })
            .on('click', function (span) {
                selectedSpan = span;
                updateSelection();
                notifyTimespanSelected(onTimespanSelected, selectedSpan);
                d3.event.preventDefault();
            });

        updateSelection();

        return {
            widget: container.node(),
            load: function (onTimespanSelectedFunction) {
                onTimespanSelected = onTimespanSelectedFunction;

                notifyTimespanSelected(onTimespanSelected, selectedSpan);
            }
        };
    };

    return module;
}(d3);
