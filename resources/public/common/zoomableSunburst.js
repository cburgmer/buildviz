var zoomableSunburst = function (svg, diameter) {
    // Following http://bl.ocks.org/metmajer/5480307
    var radius = diameter / 2,
        color = d3.scale.category20c();

    var x = d3.scale.linear()
            .range([0, 2 * Math.PI]);

    var y = d3.scale.linear()
            .range([0, radius]);

    var partition = d3.layout.partition()
            .sort(null)
            .value(function(d) { return d.size; });

    var arc = d3.svg.arc()
            .startAngle(function(d) { return Math.max(0, Math.min(2 * Math.PI, x(d.x))); })
            .endAngle(function(d) { return Math.max(0, Math.min(2 * Math.PI, x(d.x + d.dx))); })
            .innerRadius(function(d) { return Math.max(0, y(d.y)); })
            .outerRadius(function(d) { return Math.max(0, y(d.y + d.dy)); });

    var inheritDirectParentColorForLeafs = function(d) {
        var key;
        if (d.children) {
            key = d.name;
        } else if (d.depth > 1) {
            key = d.parent.name;
        } else {
            key = d.name;
        }

        return color(key);
    };

    var computeTextRotation = function (d) {
        return (x(d.x + d.dx / 2) - Math.PI / 2) / Math.PI * 180;
    };

    var arcTween = function (d) {
        var xd = d3.interpolate(x.domain(), [d.x, d.x + d.dx]),
            yd = d3.interpolate(y.domain(), [d.y, 1]),
            yr = d3.interpolate(y.range(), [d.y ? 20 : 0, radius]);
        return function(d, i) {
            return i
                ? function(t) { return arc(d); }
            : function(t) { x.domain(xd(t)); y.domain(yd(t)).range(yr(t)); return arc(d); };
        };
    };

    // poor man's text clipping
    var maxLength = function (text, length) {
        var ellipsis = '…';
        if (text.length > length) {
            return text.substr(0, length) + ellipsis;
        } else {
            return text;
        }
    };

    var render = function (data) {
        if (!data.children.length) {
            return;
        }

        var currentVisibleNode = undefined;

        var enoughPlaceForText = function (d) {
            var currentDx = currentVisibleNode ? currentVisibleNode.dx : 1;
            return (d.dx / currentDx) > 0.015;
        };

        var displayText = function(d) {
            var currentDepth = currentVisibleNode ? currentVisibleNode.depth : 0;
            if (d.depth === 0 || d.depth < currentDepth || !enoughPlaceForText(d)) {
                return 'none';
            }
            return '';
        };

        var isParent = function (parent, elem) {
            var parentMaxY = parent.y + parent.dy,
                parentMaxX = parent.x + parent.dx;
            return parentMaxY === elem.y && parent.x <= elem.x && elem.x <= parentMaxX;
        };

        var click = function (d) {
            d3.event.preventDefault();

            currentVisibleNode = d;

            text.transition().attr("opacity", 0);

            path.transition()
                .duration(750)
                .attrTween("d", arcTween(d))
                .each("start", function () {
                    d3.select(this.parentNode)
                        .attr("display", "inherit");
                })
                .each("end", function(e, i) {
                    // check if the animated element's data e lies within the visible angle span given in d
                    if (e.x >= d.x && e.x < (d.x + d.dx)) {
                        d3.select(this.parentNode).select("text")
                            .attr("display", displayText)
                            .attr("opacity", 1)
                            .attr("transform", function() { return "rotate(" + computeTextRotation(e) + ")"; })
                            .attr("x", function(d) { return y(d.y); });
                    } else {
                        if (!isParent(e, currentVisibleNode)) {
                            d3.select(this.parentNode)
                                .attr("display", "none");
                        }
                    }
                });
        };

        var parent = svg.append("g")
                .attr("transform", "translate(" + (diameter / 2) + "," + (diameter / 2) + ")");

        var g = parent.datum(data).selectAll("g")
                .data(partition.nodes)
                .enter()
                .append("g")
                .attr('data-id', function (d) {
                    return d.id;
                })
                .style('cursor', 'pointer')
                .on('click', click);

        var path = g.append("path")
                .attr("d", arc)
                .style("stroke", "#fff")
                .style("fill", function (d) {
                    if (d.depth) {
                        return d.color || inheritDirectParentColorForLeafs(d);
                    } else {
                        return 'transparent';
                    }
                });

        var text = g.append("text")
                .attr("display", displayText)
                .attr("transform", function(d) { return "rotate(" + computeTextRotation(d) + ")"; })
                .attr("x", function(d) { return y(d.y); })
                .attr("dx", "6") // margin
                .attr("dy", ".35em") // vertical-align
                .text(function(d) { return maxLength(d.name, 15); });

        g.append("title")
            .text(function (d) {
                return d.title || d.name;
            });
    };

    return {
        render: render
    };
};
