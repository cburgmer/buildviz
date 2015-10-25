var timespan = function (d3) {
    var module = {};

    module.createSelector = function (selectedTimespan, onTimespanSelected) {
        var container = d3.select(document.createElement('div'));

        container.append("button")
            .text('all')
            .on('click', function () {
                onTimespanSelected('all');
                d3.event.preventDefault();
            });
        container.append("button")
            .text(selectedTimespan)
            .on('click', function () {
                onTimespanSelected(selectedTimespan);
                d3.event.preventDefault();
            });

        return container.node();
    };

    return module;
}(d3);
