(function (timespanSelection, graphFactory, dataSource) {
    var flakyTestsAsBubbles = function (testCase) {
        return {
            id: [testCase.job, testCase.classname, testCase.name].join("\\"),
            name: testCase.name,
            title: [testCase.name,
                    testCase.classname,
                    '',
                    testCase.job + ' (' + testCase.testsuite + ')',
                    'Latest flaky build: ' + testCase.latestBuildId,
                    '',
                    'Flaky count: ' + testCase.flakyCount,
                    'Last flaky failure: ' + testCase.latestFailure]
                .join('\n'),
            value: testCase.flakyCount,
            lastTime: Date.parse(testCase.latestFailure.replace(' ', 'T')) // HACK
        };
    };

    var bubble = d3.layout.pack()
            .sort(null)
            .size([graphFactory.size, graphFactory.size])
            .padding(1.5);

    var noGrouping = function (bubbleNodes) {
        return bubbleNodes.filter(function(d) { return d.depth > 0; });
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

    var renderData = function (data, svg) {
        var flakyTests = data.map(flakyTestsAsBubbles);

        var color = colorScale(d3.min(flakyTests, function (d) { return d.lastTime; }),
                               Date.now());

        var selection = svg.selectAll("g")
                .data(noGrouping(bubble.nodes({children: flakyTests})),
                      function (d) {
                          return d.id;
                      });

        selection.exit()
            .remove();

        var node = selection
                .enter()
                .append("g");

        node.append("title");
        node.append("circle");
        node.append("text")
            .style("text-anchor", "middle");

        selection
            .transition()
            .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });

        selection.select('title')
            .text(function(d) { return d.title; });

        selection.select("circle")
            .attr("r", function (d) { return d.r; })
            .style("fill", function(d) { return color(d.lastTime); });

        selection.select('text')
            .selectAll('*')
            .remove();
        selection.select('text')
            .each(function (d) {
                graphFactory.textWithLineBreaks(this, getReadableTestName(d));
            });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoWeeks),
        graph = graphFactory.create({
            id: 'flakyTests',
            headline: "Flaky tests",
            description: "<h3>Which tests provide questionable value and will probably be trusted the least?</h3><i>Color: flaky ratio, diameter: flaky count</i>",
            csvUrl: "/flakytestcases.csv",
            noDataReason: "provided the <code>inputs</code> for relevant builds and uploaded test results",
            widgets: [timespanSelector.widget]
        });

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.loadCSV('/flakytestcases?from='+ fromTimestamp, function (data) {
            graph.loaded();

            renderData(data, graph.svg);
        });
    });

}(timespanSelection, graphFactory, dataSource));
