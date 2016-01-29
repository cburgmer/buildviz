var jobColors = function () {
    "use strict";
    var module = {};

    var jobGroupColor,
        jobGroupRange = {},
        sameJobGroupColorSteps = 20,
        subGroupBrightnessRange = d3.range(-sameJobGroupColorSteps / 2, sameJobGroupColorSteps / 2)
            .sort(function (a, b) { return Math.abs(a) - Math.abs(b); });

    var getJobGroupRange = function (jobGroup) {
        if (! jobGroupRange[jobGroup]) {
            jobGroupRange[jobGroup] = d3.scale
                .ordinal()
                .range(subGroupBrightnessRange);
        }

        return jobGroupRange[jobGroup];
    };

    var getJobGroup = function (jobName) {
        return jobName.split(' ')[0];
    };

    var jobColor = function (jobName) {

        var jobGroup = getJobGroup(jobName),
            color = jobGroupColor(jobGroup),
            jobRange = getJobGroupRange(jobGroup);

        return d3.hsl(color)
            .brighter(jobRange(jobName) / 8)
            .toString();
    };

    var getStableJobGroups = function (jobNames) {
        var jobGroups = d3.set(jobNames.map(getJobGroup)).values();
        jobGroups.sort();
        return jobGroups;
    };

    module.colors = function (jobNames) {
        if (! jobGroupColor) {
            jobGroupColor = d3.scale.category20()
                .domain(getStableJobGroups(jobNames));
        }

        return jobColor;
    };


    return module;
}();
