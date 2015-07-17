module.exports = {
    query: function (host, multicastIP, port, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "MulticastDNSPlugin", "query", [host, multicastIP, port]);
    }
};