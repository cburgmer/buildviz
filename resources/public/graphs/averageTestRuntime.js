(function (timespanSelection, graphDescription, graphFactory, zoomableSunburst, utils, jobColors, dataSource) {
    var title = function (entry) {
        var runtime = '';
        if (entry.averageRuntime !== undefined) {
            runtime = ' (' + utils.formatTimeInMs(entry.averageRuntime, {showMillis: true}) + ')';
        }
        return entry.name + runtime;
    };

    var hasOnlyOneChild = function (children) {
        return children && children.length === 1;
    };

    var skipOnlyTestSuite = function (children) {
        var hasOnlyOneTestSuite = hasOnlyOneChild(children);

        return hasOnlyOneTestSuite ? children[0].children : children;
    };

    var concatIds = function (parentId, id) {
        return parentId + '/' + id;
    };

    var buildNodeStructure = function (hierarchy, parentId) {
        return Object.keys(hierarchy).map(function (nodeName) {
            var entry = hierarchy[nodeName],
                id = concatIds(parentId, nodeName);

            if (entry.name) {
                return {
                    name: nodeName,
                    id: id,
                    averageRuntime: entry.averageRuntime
                };
            } else {
                return {
                    name: nodeName,
                    id: id,
                    children: buildNodeStructure(entry, id)
                };
            }
        });
    };

    var buildPackageHierarchy = function (classEntries, parentId) {
        var packageHierarchy = {};

        classEntries.forEach(function (entry) {
            var packageClassName = entry.name,
                components = packageClassName.split('.'),
                packagePath = components.slice(0, -1),
                className = components.pop();

            var branch = packagePath.reduce(function (packageBranch, packageName) {
                if (!packageBranch[packageName]) {
                    packageBranch[packageName] = {};
                }
                return packageBranch[packageName];
            }, packageHierarchy);

            branch[className] = entry;
        });

        return buildNodeStructure(packageHierarchy, parentId);
    };

    var mergeSingleChildHierarchy = function (elem) {
        var children = elem.children && elem.children.map(mergeSingleChildHierarchy);

        if (hasOnlyOneChild(children) && children[0].children) {
            elem.name = elem.name + '.' + children[0].name;
            elem.children = children[0].children;
        } else if (elem.children) {
            elem.children = elem.children;
        }
        return elem;
    };

    var addAccumulatedApproximateRuntime = function (elem) {
        if (elem.children) {
            elem.children = elem.children.map(addAccumulatedApproximateRuntime);
        }
        if (elem.children && elem.children.every(function (child) { return child.averageRuntime !== undefined; })) {
            elem.averageRuntime = elem.children.reduce(function (acc, child) {
                return acc + child.averageRuntime;
            }, 0);
        }
        return elem;
    };

    var addTitle = function (elem) {
        elem.title = title(elem);
        if (elem.children) {
            elem.children = elem.children.map(addTitle);
        }
        return elem;
    };

    var toSunburstFormat = function (elem) {
        elem.size = elem.averageRuntime;
        if (elem.children) {
            elem.children = elem.children.map(toSunburstFormat);
        }
        return elem;
    };

    var transformClasses = function (classNodes, parentId) {
        return buildPackageHierarchy(classNodes, parentId)
            .map(addAccumulatedApproximateRuntime)
            .map(mergeSingleChildHierarchy)
            .map(addTitle)
            .map(toSunburstFormat);
    };

    var transformTestSuite = function (node, parentId) {
        if (!node.children) {
            return transformClasses([node], parentId)[0];
        }

        var classNodes = node.children.filter(function (child) {
            return !child.children;
        });

        var nestedSuites = node.children.filter(function (child) {
            return child.children;
        });

        var id = concatIds(parentId, node.name);

        return {
            name: node.name,
            id: id,
            children: transformClasses(classNodes, id).concat(nestedSuites.map(function(suite) {
                return transformTestSuite(suite, id);
            }))
        };
    };

    var skipParentNodesIfAllOnlyHaveOneChild = function (nodes) {
        var allHaveOneChild = nodes.reduce(function (allHaveOneChild, node) {
            return allHaveOneChild && hasOnlyOneChild(node);
        }, true);

        if (allHaveOneChild) {
            return skipParentNodesIfAllOnlyHaveOneChild(nodes.map(function (node) {
                return node.children[0];
            }));
        } else {
            return nodes;
        }
    };

    var transformTestsuites = function (testclassesByJob) {
        var jobNames = testclassesByJob.map(function (jobEntry) {
            return jobEntry.jobName;
        });
        var color = jobColors.colors(jobNames);

        return testclassesByJob
            .filter(function (jobEntry) {
                return jobEntry.children.length > 0;
            })
            .map(function (jobEntry) {
                var jobName = jobEntry.jobName,
                    children = jobEntry.children;

                return {
                    name: jobName,
                    color: color(jobName),
                    id: 'jobname-' + jobName,
                    children: skipParentNodesIfAllOnlyHaveOneChild(skipOnlyTestSuite(children.map(function (child) {
                        return transformTestSuite(child, jobName);
                    })))
                };
            });
    };

    var timespanSelector = timespanSelection.create(timespanSelection.timespans.sevenDays),
        description = graphDescription.create({
            description: "Average runtime of tests per class/file by job. Runtimes of test cases are added up by test class/file and grouped by package hierarchy. Where unambiguous, test suites are omitted and package paths merged, to avoid unneccessary deep nesting.",
            answer: ["Where is the time spent in testing?"],
            legend: 'Color: job/test suite, arc size: average runtime of tests per class/file',
            csvSource: "testclasses.csv"
        }),
        graph = graphFactory.create({
            id: 'averageTestRuntime',
            headline: "Average test class runtime",
            noDataReason: "uploaded test results",
            widgets: [timespanSelector.widget, description.widget]
        });
    var sunburst = zoomableSunburst(graph.svg, graphFactory.size);

    timespanSelector.load(function (fromTimestamp) {
        graph.loading();

        dataSource.load('testclasses?from='+ fromTimestamp, function (testsuites) {
            graph.loaded();

            var data = {
                name: "Testsuites",
                id: '__testsuites__',
                children: transformTestsuites(testsuites)
            };

            sunburst.render(data);
        });
    });
}(timespanSelection, graphDescription, graphFactory, zoomableSunburst, utils, jobColors, dataSource));
