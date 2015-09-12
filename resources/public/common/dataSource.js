var dataSource = function () {
    var module = {};

    module.load = function (url, callback) {
        d3.json(url, function (_, data) {
            callback(data);
        });
    };

    return module;
}();
