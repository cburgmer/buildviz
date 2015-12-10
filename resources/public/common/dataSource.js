var dataSource = function () {
    var module = {};

    var loadHandlersByType = {};

    var loadHandlers = function (type) {
        loadHandlersByType[type] = loadHandlersByType[type] || {};
        return loadHandlersByType[type];
    };

    var load = function (type, url, handler) {
        var handlers = loadHandlers(type)[url] || [];
        handlers.push(handler);
        loadHandlers(type)[url] = handlers;

        if (handlers.length === 1) {
            if (type === 'json') {
                d3.json(url, function (error, data) {
                    callbackHandlers(type, url, error, data);
                });
            } else {
                d3.csv(url, function (error, data) {
                    callbackHandlers(type, url, error, data);
                });
            }
        }
    };

    var callbackHandlers = function (type, url, error, data) {
        var handlers = loadHandlers(type)[url];
        loadHandlers(type)[url] = undefined;

        if (error) {
            console.warn(error);
        } else {
            handlers.forEach(function (handler) {
                handler(data);
            });
        }
    };

    module.load = function (url, callback) {
        load('json', url, callback);
    };

    module.loadCSV = function (url, callback) {
        load('csv', url, callback);
    };

    return module;
}();
