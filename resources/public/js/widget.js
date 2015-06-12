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

    module.create = function (headline,description) {
        var widget = d3.select("body").append("section");
        widget.append("h1")
            .text(headline);

        widget.append("p")
            .text(description)
        return {
            svg: function (size) {
                return responsiveSvg(widget, size);
            }
        };
    };

    return module;
}();
