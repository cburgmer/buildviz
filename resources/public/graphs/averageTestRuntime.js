(function (graphFactory, zoomableSunburst, utils, jobColors, dataSource) {
    var title = function (entry) {
        return entry.name + ' (' + utils.formatTimeInMs(entry.averageRuntime, {showMillis: true}) + ')';
    };

    var hasOnlyOneChild = function (children) {
        return children && children.length === 1;
    };

    var skipOnlyTestSuite = function (children) {
        var hasOnlyOneTestSuite = hasOnlyOneChild(children);

        return hasOnlyOneTestSuite ? children[0].children : children;
    };

    var buildNodeStructure = function (hierarchy) {
        return Object.keys(hierarchy).map(function (nodeName) {
            var entry = hierarchy[nodeName];

            if (entry.name) {
                return {
                    name: nodeName,
                    averageRuntime: entry.averageRuntime
                };
            } else {
                return {
                    name: nodeName,
                    children: buildNodeStructure(entry)
                };
            }
        });
    };

    var buildPackageHierarchy = function (classEntries) {
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

        return buildNodeStructure(packageHierarchy);
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

    var transformClasses = function (classNodes) {
        return buildPackageHierarchy(classNodes)
            .map(addAccumulatedApproximateRuntime)
            .map(mergeSingleChildHierarchy)
            .map(addTitle)
            .map(toSunburstFormat);
    };

    var transformTestSuite = function (node) {
        if (!node.children) {
            return transformClasses([node])[0];
        }

        var classNodes = node.children.filter(function (child) {
            return !child.children;
        });

        var nestedSuites = node.children.filter(function (child) {
            return child.children;
        });

        return {
            name: node.name,
            children: transformClasses(classNodes).concat(nestedSuites.map(transformTestSuite))
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

    var transformTestsuites = function (jobMap) {
        var jobNames = Object.keys(jobMap),
            color = jobColors.colors(jobNames);

        return jobNames
            .filter(function (jobName) {
                return jobMap[jobName].children.length > 0;
            })
            .map(function (jobName) {
                var job = jobMap[jobName],
                    children = job.children;

                return {
                    name: jobName,
                    color: color(jobName),
                    id: 'jobname-' + jobName,
                    children: skipParentNodesIfAllOnlyHaveOneChild(skipOnlyTestSuite(children.map(transformTestSuite)))
                };
            });
    };

    var timestampOneWeekAgo = function () {
        var today = new Date(),
            oneWeekAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 7);
        return +oneWeekAgo;
    };

    var graph = graphFactory.create({
        id: 'averageTestRuntime',
        headline: "Average test runtime",
        description: "<h3>Where is the time spent in testing?</h3><i>Color: job/test suite, arc size: duration</i>",
        csvUrl: "/testclasses.csv",
        noDataReason: "uploaded test results"
    });
    var sunburst = zoomableSunburst(graph.svg, graphFactory.size);

    graph.loading();

    dataSource.load('/testclasses?from='+ timestampOneWeekAgo(), function (testsuites) {
        graph.loaded();

        var data = {
            name: "Testsuites",
            children: transformTestsuites(testsuites)
        };

        sunburst.render(data);
    });
}(graphFactory, zoomableSunburst, utils, jobColors, dataSource));
