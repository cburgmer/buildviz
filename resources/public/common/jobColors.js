const jobColors = (function () {
    "use strict";
    const module = {};

    let jobColor;

    module.colors = function (jobNames) {
        if (!jobColor) {
            jobNames.sort();
            jobColor = d3.scale.category20().domain(jobNames);
        }

        return jobColor;
    };

    return module;
})();
