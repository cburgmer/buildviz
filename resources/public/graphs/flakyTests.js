(function (timespanSelection, graphDescription, jobColors, graphFactory, dataSource) {
    var borderWidthInPx = 12;

    var flakyTestsAsBubbles = function (testCase) {
        return {
            id: [testCase.job, testCase.classname, testCase.name].join("\\"),
            name: testCase.name,
            job: testCase.job,
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

    var ageColorScale = function (minDomain, maxDomain) {
        return d3.scale.linear()
            .domain([minDomain, maxDomain])
            .range([d3.rgb('white').darker(0.3), d3.rgb('black')])
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

        var jobNames = d3.set(flakyTests.map(function (test) {
            return test.job;
        })).values();
        var jobColor = jobColors.colors(jobNames);

        var ageColor = ageColorScale(d3.min(flakyTests, function (d) { return d.lastTime; }),
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
            .attr("r", function (d) {
                // Discount for stroke taking up part of the circle. Half of the stroke grows inside, half outside.
                var radius = d.r - borderWidthInPx / 2;
                return Math.max(radius, d.r / 2);
            })
            .style('stroke', function (d) {
                return ageColor(d.lastTime);
            })
            .style('stroke-width', function (d) {
                return Math.min(borderWidthInPx, d.r);
            })
            .style('fill', function (d) {
                return jobColor(d.job);
            });

        selection.select('text')
            .selectAll('*')
            .remove();
        selection.select('text')
            .each(function (d) {
                graphFactory.textWithLineBreaks(this, getReadableTestName(d));
            });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.twoWeeks),
        description = graphDescription.create({
            description: "All flaky test cases. A test case is considered flaky if it failed in one build, but passed in another, given that both builds were run with the same inputs. Multiple test cases with the same name have their flaky failure counts added up.",
            answer: ["Which tests provide questionable value and will probably be trusted the least?"],
            legend: "Border color: age of last flaky failure, inner color: job, diameter: flaky count",
            csvSource: "/flakytestcases.csv"
        }),
        graph = graphFactory.create({
            id: 'flakyTests',
            headline: "Flaky tests cases",
            noDataReason: "provided the <code>inputs</code> for relevant builds and uploaded test results",
            widgets: [timespanSelector.widget, description.widget]
        });

    timespanSelector.load(function (selectedTimespan) {
        var fromTimestamp = timespanSelection.startingFromTimestamp(selectedTimespan);

        graph.loading();

        dataSource.loadCSV('/flakytestcases?from='+ fromTimestamp, function (data) {
            graph.loaded();

            renderData(data, graph.svg);
        });
    });

}(timespanSelection, graphDescription, jobColors, graphFactory, dataSource));
