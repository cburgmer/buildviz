var zoomableSunburst = function (svg, diameter) {
    var zoomTransitionDuration = 750,
        maxCaptionCharacterCount = diameter / 16;

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
            return text.substr(0, length - 1) + ellipsis;
        } else {
            return text;
        }
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

    var allChildNodesFor = function (parentNode) {
        if (!parentNode.children) {
            return [parentNode];
        }

        return parentNode.children.reduce(function (nodes, childNode) {
            return nodes.concat(allChildNodesFor(childNode));
        }, [parentNode]);
    };

    var allNodesFor = function (node) {
        var nodes = allChildNodesFor(node);
        if (node.parent) {
            nodes.push(node.parent);
        }
        return nodes;
    };

    var renderSunburst = function (rootNode, domElement, showTransition) {
        var nodes = allNodesFor(rootNode);

        var selection = domElement
                .selectAll('g')
                .data(nodes,
                      function (d) {
                          return d.id;
                      });

        // enter
        var g = selection
                .enter()
                .append("g")
                .attr('data-id', function (d) {
                    return d.id;
                })
                .on('click', function (d) {
                    d3.event.preventDefault();

                    renderSunburst(d, domElement, true);
                });

        selection.attr('role', function (d) {
            if (d.depth - rootNode.depth <= 1) {
                return 'link';
            }
            return undefined;
        });

        g.append("path")
            .style("fill", function (d) {
                if (d.depth) {
                    return d.color || inheritDirectParentColorForLeafs(d);
                } else {
                    return 'transparent';
                }
            });

        g.append("text")
            .attr("dx", "6") // margin
            .attr("dy", ".35em") // vertical-align
            .text(function(d) {
                if (d.children) {
                    return maxLength(d.name, maxCaptionCharacterCount * d.dy);
                }
                var totalDepthCount = 1.0 / d.dy,
                    depthsLeft = totalDepthCount - d.depth;
                return maxLength(d.name, maxCaptionCharacterCount * depthsLeft * d.dy);
            });

        g.append("title")
            .text(function (d) {
                return d.title || d.name;
            });

        // data
        selection.select('text')
            .attr("display", 'none');

        selection.select('path')
            .transition()
            .duration(showTransition ? zoomTransitionDuration : 0)
            .attrTween("d", arcTween(rootNode))
            .each("end", function(e) {
                d3.select(this.parentNode)
                    .select("text")
                    .attr("display", function (d) {
                        return displayText(d, rootNode) ? 'inherit' : 'none';
                    })
                    .attr("transform", function(d) { return "rotate(" + computeTextRotation(d) + ")"; })
                    .attr("x", function(d) { return y(d.y); });
            });

        // exit
        var exit = selection
                .exit();

        exit.select('text')
            .remove();

        exit.select('path')
            .transition()
            .duration(showTransition ? zoomTransitionDuration : 0)
            .attrTween("d", arcTween(rootNode))
            .each("end", function() {
                d3.select(this.parentNode)
                    .remove();
            });
    };

    var render = function (data) {
        if (!data.children.length) {
            removeRootPane();
            return;
        }

        renderSunburst(partition.nodes(data)[0], getOrCreateRootPane(), false);
    };

    return {
        render: render
    };
};
