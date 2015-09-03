(function (widget, zoomableSunburst, utils) {
    var diameter = 600;

    var svg = widget.create("Avg test runtime by class",
                            "Color: Job/Test Suite, Arc size: duration",
                            "/testclasses.csv")
            .svg(diameter);

    var graph = zoomableSunburst(svg, diameter);

    var title = function (entry) {
        var runtime = entry.averageRuntime ? ' (' + utils.formatTimeInMs(entry.averageRuntime, {showMillis: true}) + ')' : '';
        return entry.name + runtime;
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

    var buildPackageHiearchy = function (classEntries) {
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

    var transformClassNode = function (elem) {
        var e = {
            name: elem.name
        };

        if (elem.children) {
            e.children = elem.children.map(transformClassNode);
        } else {
            e.size = elem.averageRuntime;
            e.title = title(elem);
        }
        return e;
    };

    var transformTestSuite = function (node) {
        if (!node.children) {
            return buildPackageHiearchy([node]).map(transformClassNode)[0];
        }

        var leafNodes = node.children.filter(function (child) {
            return !child.children;
        });

        var nestedSuites = node.children.filter(function (child) {
            return child.children;
        });

        return {
            name: node.name,
            children: buildPackageHiearchy(leafNodes).map(transformClassNode).concat(nestedSuites.map(transformTestSuite))
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
        return Object.keys(jobMap)
            .map(function (jobName) {
                var job = jobMap[jobName],
                    children = job.children;

                return {
                    name: jobName,
                    children: skipParentNodesIfAllOnlyHaveOneChild(skipOnlyTestSuite(children.map(transformTestSuite)))
                };
            });
    };

    d3.json('/testclasses', function (_, testsuites) {
        var data = {
            name: "Testsuites",
            children: transformTestsuites(testsuites)
        };

        graph.render(data);
    });
}(widget, zoomableSunburst, utils));
