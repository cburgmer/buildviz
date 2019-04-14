const badJobs = (function(jobColors, utils) {
    "use strict";

    const module = {};

    const borderWidthInPx = 30;

    const selectWorst = function(pipeline, n) {
        pipeline.sort(function(jobA, jobB) {
            return jobA.value - jobB.value;
        });

        return pipeline.slice(-n);
    };

    const bubble = d3.layout
            .pack()
            .sort(null)
            .size([graphFactory.size, graphFactory.size])
            .padding(1.5),
        noGrouping = function(bubbleNodes) {
            return bubbleNodes.filter(function(d) {
                return d.depth > 0;
            });
        };

    const colorScale = function(maxDomain) {
        return d3.scale
            .linear()
            .domain([0, maxDomain])
            .range(["white", d3.rgb("red").darker()])
            .interpolate(d3.interpolateLab);
    };

    module.renderData = function(data, svg, jobCount, worstFailureRatio) {
        svg.attr("class", "badJobs");

        const jobNames = data.map(function(job) {
            return job.name;
        });
        const jobColor = jobColors.colors(jobNames),
            worstJobs = selectWorst(data, jobCount);

        const color = colorScale(worstFailureRatio);

        const selection = svg
            .selectAll("g")
            .data(noGrouping(bubble.nodes({ children: worstJobs })), function(
                d
            ) {
                return d.name;
            });

        selection.exit().remove();

        const node = selection
            .enter()
            .append("g")
            .on("mouseover", function(d) {
                window.dispatchEvent(
                    new CustomEvent("jobSelected", {
                        detail: { jobName: d.name }
                    })
                );
            })
            .on("mouseout", function() {
                window.dispatchEvent(
                    new CustomEvent("jobSelected", {
                        detail: { jobName: undefined }
                    })
                );
            });

        window.addEventListener("jobSelected", function(event) {
            const jobName = event.detail.jobName;
            console.log(jobName);

            svg.classed("highlighted", !!jobName);
            svg.selectAll("g").classed("highlightedElement", function(d) {
                return d.name === jobName;
            });
        });

        node.append("title");
        node.append("circle")
            .attr("stroke-width", borderWidthInPx)
            .style("fill", function(d) {
                return jobColor(d.name);
            });
        node.append("text")
            .style("text-anchor", "middle")
            .each(function(d) {
                graphFactory.textWithLineBreaks(
                    this,
                    utils.breakJobName(d.name)
                );
            });

        selection.transition().attr("transform", function(d) {
            return "translate(" + d.x + "," + d.y + ")";
        });

        selection.select("title").text(function(d) {
            return d.title;
        });

        selection
            .select("circle")
            .transition()
            .attr("r", function(d) {
                return d.r - borderWidthInPx / 2;
            })
            .style("stroke", function(d) {
                return color(d.ratio);
            });
    };

    return module;
})(jobColors, utils);
