// Simple debouncing so we save requests on start-up for same URLs
const dataSource = (function() {
    const module = {};

    const loadHandlersByType = {};

    const loadHandlers = function(type) {
        loadHandlersByType[type] = loadHandlersByType[type] || {};
        return loadHandlersByType[type];
    };

    const queueHandler = function(type, url, handler) {
        const handlers = loadHandlers(type)[url] || [];
        handlers.push(handler);
        loadHandlers(type)[url] = handlers;
        return handlers;
    };

    const unqueueHandlers = function(type, url) {
        const handlers = loadHandlers(type)[url];
        loadHandlers(type)[url] = undefined;

        return handlers;
    };

    const load = function(type, url, handler) {
        const handlers = queueHandler(type, url, handler),
            isFirstHandler = handlers.length === 1;

        if (isFirstHandler) {
            if (type === "json") {
                d3.json(url, function(error, data) {
                    callbackHandlers(type, url, error, data);
                });
            } else {
                d3.csv(url, function(error, data) {
                    callbackHandlers(type, url, error, data);
                });
            }
        }
    };

    const callbackHandlers = function(type, url, error, data) {
        const handlers = unqueueHandlers(type, url);

        if (error) {
            console.warn(error);
        } else {
            handlers.forEach(function(handler) {
                handler(data);
            });
        }
    };

    module.load = function(url, callback) {
        load("json", url, callback);
    };

    module.loadCSV = function(url, callback) {
        load("csv", url, callback);
    };

    return module;
})();
