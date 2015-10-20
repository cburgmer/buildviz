var dataSource = function () {
    var module = {};

    module.load = function (url, callback) {
        d3.json(url, function (error, data) {
            if (error) return console.warn(error);

            callback(data);
        });
    };

    module.loadCSV = function (url, callback) {
        d3.csv(url, function (error, data) {
            if (error) return console.warn(error);

            callback(data);
        });
    };

    return module;
}();
