var tooltip = function () {
    var tooltip = d3.select("body").append("div")
            .attr("class", "tooltip")
            .style("display", "none");

    var pointerIsOnLeftSideOfScreen = function () {
        var windowRect = document.body.getBoundingClientRect();
        return d3.event.pageX < (windowRect.width / 2);
    };

    var mouseover = function (html) {
        var targetRect = d3.event.target.getBoundingClientRect(),
            windowRect = document.body.getBoundingClientRect();
        tooltip
            .style("display", "inline")
            .html(html)
            .style("top", (window.scrollY + targetRect.top + (targetRect.height / 2)) + "px");
        if (pointerIsOnLeftSideOfScreen()) {
            tooltip
                .style("left", (window.scrollX + targetRect.right) + "px")
                .style("right", "");
        } else {
            tooltip
                .style("left", "")
                .style("right", (windowRect.width - window.scrollX - targetRect.left) + "px");
        }
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
