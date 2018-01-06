var tooltip = function () {
    var tooltip = d3.select("body").append("div")
            .attr("class", "tooltip")
            .style("display", "none");

    var mouseover = function (html) {
        var targetRect = d3.event.target.getBoundingClientRect();
        tooltip
            .style("display", "inline")
            .html(html)
            .style("left", (window.scrollX + targetRect.right) + "px")
            .style("top", (window.scrollY + targetRect.top + (targetRect.height / 2)) + "px");
    };

    var mouseout = function () {
        tooltip.style("display", "none");
    };

    var register = function (element, htmlFactory) {
        element
            .on("mouseover", function (d) { mouseover(htmlFactory(d)); })
            .on("mouseout", mouseout);
    };


    return {
        register: register
    };
}();
