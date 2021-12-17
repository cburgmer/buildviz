const tooltip = (function () {
    const tooltip = d3
        .select("body")
        .append("div")
        .attr("class", "tooltip")
        .style("display", "none");

    const pointerIsOnLeftSideOfScreen = function () {
        const windowRect = document.body.getBoundingClientRect();
        return d3.event.pageX < windowRect.width / 2;
    };

    const mouseover = function (html) {
        const targetRect = d3.event.target.getBoundingClientRect(),
            windowRect = document.body.getBoundingClientRect();

        if (!html) {
            return;
        }

        tooltip
            .style("display", "inline")
            .html(html)
            .style("top", d3.event.pageY + 20 + "px");
        if (pointerIsOnLeftSideOfScreen()) {
            tooltip
                .style("left", d3.event.pageX + 10 + "px")
                .style("right", "");
        } else {
            tooltip
                .style("left", "")
                .style("right", windowRect.width - d3.event.pageX + 10 + "px");
        }
    };

    const mouseout = function () {
        tooltip.style("display", "none");
    };

    const register = function (element, htmlFactory) {
        element
            .on("mouseover.tooltip", function (d) {
                mouseover(htmlFactory(d));
            })
            .on("mouseout.tooltip", mouseout);
    };

    return {
        register: register,
    };
})();
