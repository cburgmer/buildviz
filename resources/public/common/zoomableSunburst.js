const zoomableSunburst = function (svg, diameter) {
    const zoomTransitionDuration = 750,
        maxCaptionCharacterCount = diameter / 16;

    let rootPane;
    const getOrCreateRootPane = function () {
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

    const radius = diameter / 2;

    const x = d3.scale.linear()
            .range([0, 2 * Math.PI]);

    const y = d3.scale.linear()
            .range([0, radius]);

    const partition = d3.layout.partition()
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

    const arc = d3.svg.arc()
            .startAngle(function(d) { return Math.max(0, Math.min(2 * Math.PI, x(d.x))); })
            .endAngle(function(d) { return Math.max(0, Math.min(2 * Math.PI, x(d.x + d.dx))); })
            .innerRadius(function(d) { return Math.max(0, y(d.y)); })
            .outerRadius(function(d) { return Math.max(0, y(d.y + d.dy)); });

    const computeTextRotation = function (d) {
        return (x(d.x + d.dx / 2) - Math.PI / 2) / Math.PI * 180;
    };

    const arcTween = function (d) {
        const xd = d3.interpolate(x.domain(), [d.x, d.x + d.dx]),
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

    const nodeLuminance = function (d, l) {
        if (!d.value) {
            return 50;
        }
        const luminance = d3.scale.sqrt()
                .domain([0, 1e6])
                .range([90, 40]);

        return luminance(d.value);
    };

    const closestAncestorWithColor = function (d) {
        let parent = d;
        while (!parent.color && parent.parent) {
            parent = parent.parent;
        }
        return parent;
    };

    const nodeColorWithFallbackToParentColor = function (d) {
        let color = d.color;
        if (! color) {
            const ancestor = closestAncestorWithColor(d);
            color = d3.lab(ancestor.color);
            color.l = nodeLuminance(d, color.l);
        }
        return color;
    };

    // poor man's text clipping
    const maxLength = function (text, length) {
        const ellipsis = '…';
        if (text.length > length) {
            return text.substr(0, length - 1) + ellipsis;
        } else {
            return text;
        }
    };

    const enoughPlaceForText = function (d, selectedNode) {
        const currentDx = selectedNode ? selectedNode.dx : 1;
        return (d.dx / currentDx) > 0.015;
    };

    const displayText = function(d, selectedNode) {
        const currentDepth = selectedNode ? selectedNode.depth : 0;
        if (d.depth === 0 || d.depth < currentDepth || !enoughPlaceForText(d, selectedNode)) {
            return false;
        }
        return true;
    };

    const allChildNodesFor = function (parentNode) {
        if (!parentNode.children) {
            return [parentNode];
        }

        return parentNode.children.reduce(function (nodes, childNode) {
            return nodes.concat(allChildNodesFor(childNode));
        }, [parentNode]);
    };

    const allNodesFor = function (node) {
        const nodes = allChildNodesFor(node);
        if (node.parent) {
            nodes.push(node.parent);
        }
        return nodes;
    };

    const renderSunburst = function (rootNode, domElement, showTransition) {
        const nodes = allNodesFor(rootNode);

        const selection = domElement
                .selectAll('g')
                .data(nodes,
                      function (d) {
                          return d.id;
                      });

        // enter
        const g = selection
            .enter()
            .append("g")
            .attr('class', 'segment')
            .on('click', function (d) {
                d3.event.preventDefault();

                renderSunburst(d, domElement, true);
            })
            .on("mouseover", function(d) {
                if (d.depth === 1) {
                    window.dispatchEvent(new CustomEvent('jobSelected', {detail: {jobName: d.id.replace('jobname-', '')}}));
                }
            })
            .on('mouseout', function () {
                window.dispatchEvent(new CustomEvent('jobSelected', {detail: {jobName: undefined}}));
            });

        window.addEventListener('jobSelected', function (event) {
            const jobName = event.detail.jobName;

            svg.classed('highlighted', !!jobName);
            svg.selectAll(".segment")
                .classed('highlightedElement', function (d) {
                    return (d.depth === 1 && d.id.replace('jobname-', '') === jobName) ||
                        (d.depth > 1 && d.id.replace('jobname-', '').startsWith(jobName + '/'));
                });
        });

        selection.attr('role', function (d) {
            if (d.depth - rootNode.depth <= 1) {
                return 'link';
            }
            return undefined;
        });

        g.append("path")
            .style("fill", nodeColorWithFallbackToParentColor);

        g.append("text")
            .attr("dx", "6") // margin
            .attr("dy", ".35em") // vertical-align
            .text(function(d) {
                if (d.children) {
                    return maxLength(d.name, maxCaptionCharacterCount * d.dy);
                }
                const totalDepthCount = 1.0 / d.dy,
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
        const exit = selection
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

    const render = function (data) {
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
