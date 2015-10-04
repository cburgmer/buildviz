(function (widget, dataSource) {
    var diameter = 600;

    var flakyTestsAsBubbles = function (testCase) {
        return {
            name: testCase.name,
            title: [testCase.name,
                    testCase.classname,
                    '',
                    testCase.job + ' (' + testCase.testsuite + ')',
                    '',
                    'Flaky count: ' + testCase.flakyCount,
                    'Last flaky failure: ' + testCase.latestFailure]
                .join('\n'),
            value: testCase.flakyCount,
            lastTime: Date.parse(testCase.latestFailure.replace(' ', 'T')) // HACK
        };
    };

    var svg = widget.create("Flaky tests",
                            "Color: Age of last flakyness, Diameter: Flaky count",
                           "/flakytestcases.csv")
            .svg(diameter);

    var bubble = d3.layout.pack()
            .sort(null)
            .size([diameter, diameter])
            .padding(1.5);

    var noGrouping = function (bubbleNodes) {
        return bubbleNodes.filter(function(d) { return !d.children; });
    };

    var colorScale = function (minDomain, maxDomain) {
        return d3.scale.linear()
            .domain([minDomain, maxDomain])
            .range([d3.rgb(255, 200, 200), d3.rgb("red").darker()])
            .interpolate(d3.interpolateLab);
    };

    var getReadableTestName = function (d) {
        var maxLines = Math.floor(d.r / 12),
            lines = d.name.split(' ');

        if (maxLines < 3) {
            return [];
        }

        var displayLines = lines.slice(0, maxLines);

        if (lines.length > maxLines) {
            displayLines[maxLines - 1] += '...';
        }
        return displayLines;
    };

    d3.csv('/flakytestcases', function (data) {
        var flakyTests = data.map(flakyTestsAsBubbles);

        if (!flakyTests.length) {
            return;
        }

        var color = colorScale(d3.min(flakyTests, function (d) { return d.lastTime; }),
                               Date.now());

        var node = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: flakyTests})))
                .enter()
                .append("g")
                .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });

        node.append("title")
            .text(function(d) { return d.title; });

        node.append("circle")
            .attr("r", function (d) { return d.r; })
            .style("fill", function(d) { return color(d.lastTime); });

        node.append("text")
            .style("text-anchor", "middle")
            .each(function (d) {
                widget.textWithLineBreaks(this, getReadableTestName(d));
            });
    });
}(widget, dataSource));
