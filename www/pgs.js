/* global cordova, module */

module.exports = {
    start: function (successCallback, errorCallback) {
        cordova.exec(
            successCallback,
            errorCallback,
            'pgs', 'start',
            []
        );
    },
    stop: function (successCallback, errorCallback) {
        cordova.exec(
            successCallback,
            errorCallback,
            'pgs', 'stop',
            []
        );
    },
    configure: function (successCallback, errorCallback, config) {
        var toFloat = ['desiredAccuracy', 'minDistance', 'distanceFilter'];
        var i;
        
        
        for (i = 0; i < toFloat.length; i++) {
            var key = toFloat[i];
            config[key] = parseFloat(config[key]);
        }
        
        cordova.exec(
            successCallback,
            errorCallback,
            'pgs', 'configure',
            [config]
        );
    }
};
