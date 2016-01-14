var graphFactory = function (d3) {
    "use strict";
    var module = {};

    module.size = 600;

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

    module.create = function (params) {
        var id = 'graph_' + params.id,
            widget = d3.select(document.currentScript.parentNode)
                .append("section")
                .attr("class", "graph " + params.id)
                .attr('id', id);

        widget.append('a')
            .attr('class', 'close')
            .attr('href', '#')
            .text('╳');

        var enlargeLink = widget.append("a")
                .attr('class', 'enlarge')
                .attr("href", '#' + id);

        var header = enlargeLink.append('header');
        header.append("h1")
            .text(params.headline);

        header.append("a")
            .attr("href", params.csvUrl)
            .attr('class', 'csv')
            .text("CSV");

        if (params.description) {
            header
                .append('div')
                .attr('class', 'description')
                .text('?')
                .append('section')
                .html(params.description);
        }

        if (params.widgets) {
            params.widgets.reverse().forEach(function (widget) {
                header.node().appendChild(widget);
            }) ;
        }

        widget.append('div')
            .attr('class', 'loader');

        var svg = enlargeLink
                .append("svg")
                .attr("preserveAspectRatio", "xMinYMin meet")
                .attr("viewBox", "0 0 " + module.size + " " + module.size),
            noDataExplanation = params.noDataReason ? "<p>Recent entries will appear once you've " + params.noDataReason + "</p>": '';

        enlargeLink.append("p")
            .attr('class', 'nodata')
            .html("No data" + noDataExplanation);

        return {
            svg: svg,
            loaded: function () {
                widget.classed('loading', false);
            },
            loading: function () {
                widget.classed('loading', true);
            }
        };
    };

    return module;
}(d3);
