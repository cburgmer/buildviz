"use strict";

var testHelpers = (function () {
    var module = {};

    var dateImpl = window.Date;

    module.mockDateToday = function (today) {
        window.Date = function () {
            if (arguments.length === 0) {
                return new dateImpl(today);
            } else {
                var factoryFunction = dateImpl.bind.apply(dateImpl, [null].concat(Array.prototype.slice.call(arguments)));
                return new factoryFunction();
            }
        };
    };

    module.selectTimespan = function (buttonText) {
        var button = Array.prototype.find.call(document.querySelectorAll('button'), function (element) {
            return element.innerText == buttonText;
        });
        button.click();
    };

    return module;
} ());
