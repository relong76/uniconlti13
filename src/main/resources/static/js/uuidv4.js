// Defines the global uuidv4() the launch pages use for postMessage ids, served locally instead of from a CDN
(function () {
  "use strict";

  window.uuidv4 = function () {
    if (typeof crypto.randomUUID === "function") {
      return crypto.randomUUID();
    }

    // RFC 4122 version 4 from random bytes, for browsers or contexts without randomUUID
    var bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;

    var hex = Array.prototype.map.call(bytes, function (b) {
      return (b + 0x100).toString(16).slice(1);
    }).join("");

    return hex.slice(0, 8) + "-" + hex.slice(8, 12) + "-" + hex.slice(12, 16) + "-" + hex.slice(16, 20) + "-" + hex.slice(20);
  };
})();
