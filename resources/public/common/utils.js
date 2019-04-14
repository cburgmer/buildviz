const utils = (function() {
    const module = {};

    module.breakJobName = function(jobName) {
        return jobName
            .split(/([ _\-])/)
            .filter(function removeWhiteSpace(token) {
                return token !== " ";
            })
            .reduce(function(tokens, currentToken) {
                if (["-", "_"].indexOf(currentToken) >= 0 && tokens.length) {
                    return tokens
                        .slice(0, -1)
                        .concat(tokens.pop() + currentToken);
                }
                return tokens.concat(currentToken);
            }, []);
    };

    const padZero = function(value, padToLength) {
        let paddedValue = String(value);
        while (paddedValue.length < padToLength) {
            paddedValue = "0" + paddedValue;
        }
        return paddedValue;
    };

    const formatHMS = function(values) {
        return [values[0]]
            .concat(
                values.slice(1).map(function(val) {
                    return padZero(val, 2);
                })
            )
            .join(":");
    };

    module.formatTimeInMs = function(timeInMs, options) {
        const hours = Math.floor(timeInMs / (60 * 60 * 1000)),
            minutes = Math.floor((timeInMs % (60 * 60 * 1000)) / (60 * 1000)),
            rawSeconds = (timeInMs % (60 * 1000)) / 1000,
            millis = timeInMs % 1000,
            hms = [];

        if (options && options.showMillis) {
            if (hours > 0) {
                hms.push(hours);
            }

            hms.push(minutes);
            hms.push(Math.floor(rawSeconds));
            return formatHMS(hms) + "." + padZero(Math.floor(millis), 3);
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
})();
