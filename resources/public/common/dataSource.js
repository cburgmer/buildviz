// Simple debouncing so we save requests on start-up for same URLs
var dataSource = function () {
    var module = {};

    var loadHandlersByType = {};

    var loadHandlers = function (type) {
        loadHandlersByType[type] = loadHandlersByType[type] || {};
        return loadHandlersByType[type];
    };

    var queueHandler = function (type, url, handler) {
        var handlers = loadHandlers(type)[url] || [];
        handlers.push(handler);
        loadHandlers(type)[url] = handlers;
        return handlers;
    };

    var unqueueHandlers = function (type, url) {
        var handlers = loadHandlers(type)[url];
        loadHandlers(type)[url] = undefined;

        return handlers;
    };

    var load = function (type, url, handler) {
        var handlers = queueHandler(type, url, handler),
            isFirstHandler = handlers.length === 1;

        if (isFirstHandler) {
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
        var handlers = unqueueHandlers(type, url);

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
