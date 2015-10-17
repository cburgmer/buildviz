var dataSource = function () {
    var module = {};

    module.load = function (url, callback) {
        d3.json(url, function (_, data) {
            callback(data);
        });
    };

    module.loadCSV = function (url, callback) {
        d3.csv(url, function (_, data) {
            callback(data);
        });
    };

    return module;
}();
