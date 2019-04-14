(function(
    timespanSelection,
    graphDescription,
    graphFactory,
    zoomableSunburst,
    utils,
    jobColors,
    dataSource
) {
    const title = function(entry) {
        let runtime = "";
        if (entry.averageRuntime !== undefined) {
            runtime =
                " (" +
                utils.formatTimeInMs(entry.averageRuntime, {
                    showMillis: true
                }) +
                ")";
        }
        return entry.name + runtime;
    };

    const hasOnlyOneChild = function(children) {
        return children && children.length === 1;
    };

    const skipOnlyTestSuite = function(children) {
        const hasOnlyOneTestSuite = hasOnlyOneChild(children);

        return hasOnlyOneTestSuite ? children[0].children : children;
    };

    const concatIds = function(parentId, id) {
        return parentId + "/" + id;
    };

    const buildNodeStructure = function(hierarchy, parentId, jobName) {
        return Object.keys(hierarchy).map(function(nodeName) {
            const entry = hierarchy[nodeName],
                id = concatIds(parentId, nodeName);

            if (entry.name) {
                return {
                    name: nodeName,
                    id: id,
                    jobName: jobName,
                    averageRuntime: entry.averageRuntime
                };
            } else {
                return {
                    name: nodeName,
                    id: id,
                    jobName: jobName,
                    children: buildNodeStructure(entry, id, jobName)
                };
            }
        });
    };

    const buildPackageHierarchy = function(classEntries, parentId, jobName) {
        const packageHierarchy = {};

        classEntries.forEach(function(entry) {
            const packageClassName = entry.name,
                components = packageClassName.split(/[\.:]/),
                packagePath = components.slice(0, -1),
                className = components.pop();

            const branch = packagePath.reduce(function(
                packageBranch,
                packageName
            ) {
                if (!packageBranch[packageName]) {
                    packageBranch[packageName] = {};
                }
                return packageBranch[packageName];
            },
            packageHierarchy);

            branch[className] = entry;
        });

        return buildNodeStructure(packageHierarchy, parentId, jobName);
    };

    const mergeSingleChildHierarchy = function(elem) {
        const children =
            elem.children && elem.children.map(mergeSingleChildHierarchy);

        if (hasOnlyOneChild(children) && children[0].children) {
            elem.name = elem.name + "." + children[0].name;
            elem.children = children[0].children;
        } else if (elem.children) {
            elem.children = elem.children;
        }
        return elem;
    };

    const addAccumulatedApproximateRuntime = function(elem) {
        if (elem.children) {
            elem.children = elem.children.map(addAccumulatedApproximateRuntime);
        }
        if (
            elem.children &&
            elem.children.every(function(child) {
                return child.averageRuntime !== undefined;
            })
        ) {
            elem.averageRuntime = elem.children.reduce(function(acc, child) {
                return acc + child.averageRuntime;
            }, 0);
        }
        return elem;
    };

    const addTitle = function(elem) {
        elem.title = title(elem);
        if (elem.children) {
            elem.children = elem.children.map(addTitle);
        }
        return elem;
    };

    const toSunburstFormat = function(elem) {
        elem.size = elem.averageRuntime;
        if (elem.children) {
            elem.children = elem.children.map(toSunburstFormat);
        }
        return elem;
    };

    const transformClasses = function(classNodes, parentId, jobName) {
        return buildPackageHierarchy(classNodes, parentId, jobName)
            .map(addAccumulatedApproximateRuntime)
            .map(mergeSingleChildHierarchy)
            .map(addTitle)
            .map(toSunburstFormat);
    };

    const transformTestSuite = function(node, parentId, jobName) {
        if (!node.children) {
            return transformClasses([node], parentId, jobName)[0];
        }

        const classNodes = node.children.filter(function(child) {
            return !child.children;
        });

        const nestedSuites = node.children.filter(function(child) {
            return child.children;
        });

        const id = concatIds(parentId, node.name);

        return {
            name: node.name,
            id: id,
            jobName: jobName,
            children: transformClasses(classNodes, id, jobName).concat(
                nestedSuites.map(function(suite) {
                    return transformTestSuite(suite, id, jobName);
                })
            )
        };
    };

    const skipParentNodesIfAllOnlyHaveOneChild = function(nodes) {
        const allHaveOneChild = nodes.reduce(function(allHaveOneChild, node) {
            return allHaveOneChild && hasOnlyOneChild(node);
        }, true);

        if (allHaveOneChild) {
            return skipParentNodesIfAllOnlyHaveOneChild(
                nodes.map(function(node) {
                    return node.children[0];
                })
            );
        } else {
            return nodes;
        }
    };

    const transformTestsuites = function(testclassesByJob) {
        const jobNames = testclassesByJob.map(function(jobEntry) {
            return jobEntry.jobName;
        });
        const color = jobColors.colors(jobNames);

        return testclassesByJob
            .filter(function(jobEntry) {
                return jobEntry.children.length > 0;
            })
            .map(function(jobEntry) {
                const jobName = jobEntry.jobName,
                    children = jobEntry.children;

                return {
                    name: jobName,
                    color: color(jobName),
                    id: "jobname-" + jobName,
                    jobName: jobName,
                    children: skipParentNodesIfAllOnlyHaveOneChild(
                        skipOnlyTestSuite(
                            children.map(function(child) {
                                return transformTestSuite(
                                    child,
                                    jobName,
                                    jobName
                                );
                            })
                        )
                    )
                };
            });
    };

    const timespanSelector = timespanSelection.create(
            timespanSelection.timespans.sevenDays
        ),
        description = graphDescription.create({
            description: [
                "Average runtime of tests per class/file by job.",
                "Runtimes of test cases are added up by test class/file and grouped by package hierarchy.",
                "Where unambiguous, test suites are omitted and package paths merged,",
                "to avoid unneccessary deep nesting."
            ].join(" "),
            answer: ["Where is the time spent in testing?"],
            legend:
                "Color: job/test suite, arc size: average runtime of tests per class/file",
            csvSource: "testclasses.csv"
        }),
        graph = graphFactory.create({
            id: "averageTestRuntime",
            headline: "Average test class runtime",
            noDataReason: "uploaded test results",
            widgets: [timespanSelector.widget, description.widget]
        });
    const sunburst = zoomableSunburst(graph.svg, graphFactory.size);

    timespanSelector.load(function(fromTimestamp) {
        graph.loading();

        dataSource.load("testclasses?from=" + fromTimestamp, function(
            testsuites
        ) {
            graph.loaded();

            const data = {
                name: "Test suites",
                id: "__testsuites__",
                color: "transparent",
                children: transformTestsuites(testsuites)
            };

            sunburst.render(data);
        });
    });
})(
    timespanSelection,
    graphDescription,
    graphFactory,
    zoomableSunburst,
    utils,
    jobColors,
    dataSource
);
