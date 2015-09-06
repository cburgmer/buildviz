var widget = function () {
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

    var responsiveSvg = function (d3Node, size) {
        return d3Node
            .append("svg")
            .attr("preserveAspectRatio", "xMinYMin meet")
            .attr("viewBox", "0 0 " + size + " " + size);
    };

    var nextId = 0;

    var uniqueId = function () {
        var id = nextId;
        nextId += 1;
        return id;
    };

    module.create = function (headline, description, csvUrl) {
        var id = 'widget_' + uniqueId(),
            widget = d3.select("body")
                .append("section")
                .attr('id', id)
                .append("a")
                .attr('class', 'enlarge')
                .attr("href", '#' + id)
                .attr('title', description);

        var header = widget.append('header');
        header.append("h1")
            .text(headline);

        header.append("a")
            .attr("href", csvUrl)
            .attr('class', 'csv')
            .text("CSV");

        return {
            svg: function (size) {
                return responsiveSvg(widget, size);
            }
        };
    };

    return module;
}();
