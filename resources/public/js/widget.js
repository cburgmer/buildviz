var widget = function () {
    var module = {};

    var responsiveSvg = function (d3Node, size) {
        return d3Node
            .append("svg")
            .attr("preserveAspectRatio", "xMinYMin meet")
            .attr("viewBox", "0 0 " + size + " " + size);
    };

    module.create = function (headline) {
        var widget = d3.select("body").append("section");
        widget.append("h1")
            .text(headline);

        return {
            svg: function (size) {
                return responsiveSvg(widget, size);
            }
        };
    };

    return module;
}();
