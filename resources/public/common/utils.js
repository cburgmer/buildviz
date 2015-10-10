var utils = (function () {
    var module = {};

    var padZero = function (value, padToLength) {
        var paddedValue = String(value);
        while (paddedValue.length < padToLength) {
            paddedValue = '0' + paddedValue;
        }
        return paddedValue;
    };

    var formatHMS = function (values) {
        return [values[0]].concat(values.slice(1).map(function (val) {
            return padZero(val, 2);
        })).join(':');
    };

    module.formatTimeInMs = function (timeInMs, options) {
        var hours = Math.floor(timeInMs / (60 * 60 * 1000)),
            minutes = Math.floor(timeInMs % (60 * 60 * 1000) / (60 * 1000)),
            rawSeconds = timeInMs % (60 * 1000) / 1000,
            millis = timeInMs % 1000,
            hms = [];

        if (options && options.showMillis) {
            if (hours > 0) {
                hms.push(hours);
            }

            hms.push(minutes);
            hms.push(Math.floor(rawSeconds));
            return formatHMS(hms) + '.' + padZero(Math.floor(millis), 3);
        } else {
            if (hours > 0) {
                hms.push(hours);
            }

            hms.push(minutes);
            hms.push(Math.round(rawSeconds));
            return formatHMS(hms);
        }
    };

    return module;
}());
