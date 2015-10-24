var widget = function () {
    "use strict";
    var module = {};

    module.textWithLineBreaks = function (elem, lines) {
        var textElem = d3.select(elem),
            lineHeight = 1.1,
            yCorrection = (lineHeight * lines.length) / 2 - 0.95;

        lines.forEach(function (line, idx) {
            textElem.append('tspan')
                .attr('x', 0)
                .attr('y', (lineHeight * idx - yCorrection) + 'em')
                .text(line);
        });
    };

    var responsiveSvg = function (d3Node, size, noDataReason) {
        var svg = d3Node
                .append("svg")
                .attr("preserveAspectRatio", "xMinYMin meet")
                .attr("viewBox", "0 0 " + size + " " + size),
            noDataExplanation = noDataReason ? "<p>Recent entries will appear once you've " + noDataReason + "</p>": '';

        d3Node.append("p")
            .attr('class', 'nodata')
            .html("No data" + noDataExplanation);

        return svg;
    };

    var nextId = 0;

    var uniqueId = function () {
        var id = nextId;
        nextId += 1;
        return id;
    };

    module.create = function (headline, description, csvUrl, noDataExplanation, defaultTimespan, onTimespanSelected) {
        var id = 'widget_' + uniqueId(),
            widget = d3.select("body")
                .append("section")
                .attr("class", "widget")
                .attr('id', id),
            enlargeLink = widget.append("a")
                .attr('class', 'enlarge')
                .attr("href", '#' + id);

        var header = enlargeLink.append('header');
        header.append("h1")
            .text(headline);

        header.append("a")
            .attr("href", csvUrl)
            .attr('class', 'csv')
            .text("CSV");

        if (defaultTimespan) {
            header.append("button")
                .text('all')
                .on('click', function () {
                    onTimespanSelected('all');
                    d3.event.preventDefault();
                });
            header.append("button")
                .text(defaultTimespan)
                .on('click', function () {
                    onTimespanSelected(defaultTimespan);
                    d3.event.preventDefault();
                });
        }

        header
            .append('div')
            .attr('class', 'description')
            .text('?')
            .append('section')
            .html(description);

        widget.append('div')
            .attr('class', 'loader');

        widget.classed('loading', true);

        return {
            svg: function (size) {
                return responsiveSvg(enlargeLink, size, noDataExplanation);
            },
            loaded: function () {
                widget.classed('loading', false);
            }
        };
    };

    return module;
}();
