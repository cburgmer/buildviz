var zoomableSunburst = function (svg, diameter) {
    // Following http://bl.ocks.org/metmajer/5480307

    var rootPane,
        getOrCreateRootPane = function () {
            if (!rootPane) {
                rootPane = svg.append("g")
                    .attr("transform", "translate(" + (diameter / 2) + "," + (diameter / 2) + ")");
            }
            return rootPane;
        },
        removeRootPane = function () {
            svg.select('g').remove();
            rootPane = undefined;
        };

    svg.attr('class', 'zoomableSunburst');

    var radius = diameter / 2,
        color = d3.scale.category20c();

    var x = d3.scale.linear()
            .range([0, 2 * Math.PI]);

    var y = d3.scale.linear()
            .range([0, radius]);

    var partition = d3.layout.partition()
            .sort(function (a, b) {
                if (a.name === b.name) {
                    return 0;
                } else if (a.name > b.name) {
                    return 1;
                } else {
                    return -1;
                }
            })
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
            if (i === 0) {
                return function(t) {
                    x.domain(xd(t));
                    y.domain(yd(t)).range(yr(t));
                    return arc(d);
                };
            } else {
                return function(t) {
                    return arc(d);
                };
            }
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

    var isParentNode = function (potentialParentNode, node) {
        var parentMaxY = potentialParentNode.y + potentialParentNode.dy,
            parentMaxX = potentialParentNode.x + potentialParentNode.dx;
        /*
         Check if the node's hierarchical y placement just follows the
         parent node's placement and the node's hierarchical x placement is
         within the interval of the parent node
         */
        return parentMaxY === node.y && potentialParentNode.x <= node.x && node.x <= parentMaxX;
    };

    var isChildNode = function (node, potentialParentNode) {
        // Check if the node's hierarchical placement lies within the interval of the parent node
        return node.x >= potentialParentNode.x && node.x < (potentialParentNode.x + potentialParentNode.dx);
    };

    var enoughPlaceForText = function (d, selectedNode) {
        var currentDx = selectedNode ? selectedNode.dx : 1;
        return (d.dx / currentDx) > 0.015;
    };

    var displayText = function(d, selectedNode) {
        var currentDepth = selectedNode ? selectedNode.depth : 0;
        if (d.depth === 0 || d.depth < currentDepth || !enoughPlaceForText(d, selectedNode)) {
            return false;
        }
        return true;
    };

    var render = function (data) {
        if (!data.children.length) {
            removeRootPane();
            return;
        }

        var initialized = false;
        var selectNode = function (selectedNode) {
            selection.select('text')
                .attr("display", 'none');

            selection.select('path')
                .transition()
                .duration(initialized ? 750 : 0)
                .attrTween("d", arcTween(selectedNode))
                .each("start", function () {
                    d3.select(this.parentNode)
                        .attr("display", "inherit");
                })
                .each("end", function(e) {
                    if (isChildNode(e, selectedNode)) {
                        d3.select(this.parentNode)
                            .select("text")
                            .attr("display", function (d) {
                                return displayText(d, selectedNode) ? 'inherit' : 'none';
                            })
                            .attr("transform", function(d) { return "rotate(" + computeTextRotation(d) + ")"; })
                            .attr("x", function(d) { return y(d.y); });
                    } else {
                        if (!isParentNode(e, selectedNode)) {
                            d3.select(this.parentNode)
                                .attr("display", "none");
                        }
                    }
                });

            initialized = true;
        };

        var parent = getOrCreateRootPane();

        var nodes = partition.nodes(data);

        var selection = parent
                .selectAll('g')
                .data(nodes,
                      function (d) {
                          if (d.id) {
                              return d.id;
                          }
                          // TODO handle missing IDs
                          return Math.random();
                      });

        selection.exit()
            .remove();

        var g = selection
                .enter()
                .append("g")
                .attr('data-id', function (d) {
                    return d.id;
                })
                .on('click', function (d) {
                    d3.event.preventDefault();

                    selectNode(d);
                });

        g.append("path");

        g.append("text")
            .attr('display', 'none')
            .attr("dx", "6") // margin
            .attr("dy", ".35em") // vertical-align
            .text(function(d) { return maxLength(d.name, 15); });

        g.append("title")
            .text(function (d) {
                return d.title || d.name;
            });

        selection.select('path')
            .style("fill", function (d) {
                if (d.depth) {
                    return d.color || inheritDirectParentColorForLeafs(d);
                } else {
                    return 'transparent';
                }
            });

        selectNode(nodes[0]);
    };

    return {
        render: render
    };
};
