// FunPixelNest SDK - WebGL bridge to the host page's window.FPN object.
//
// Host contract (set by the platform page BEFORE the Unity loader starts):
//   window.FPN = {
//     launchData: '{"gameId":"...","mode":"tournament|duel|practice","seed":"<64-bit as string>",
//                   "ticketId":"...","playerId":"...","displayName":"..."}',
//     submitScore: function (score, durationMs, eventLogJson) {},
//     quit: function () {},
//     log: function (message) {}   // optional
//   };
//
// Every function is safe when window.FPN (or any member) is missing: it returns 0 / does nothing.

mergeInto(LibraryManager.library, {

  FPN_IsHosted: function () {
    try {
      return (typeof window !== 'undefined' && window.FPN && typeof window.FPN === 'object') ? 1 : 0;
    } catch (e) {
      return 0;
    }
  },

  // Returns a UTF-8 buffer allocated with _malloc (or 0 = null). The C# side declares the return type
  // as `string`; IL2CPP copies it into a managed string and frees this buffer (Unity's documented pattern).
  FPN_GetLaunchData: function () {
    try {
      if (typeof window === 'undefined' || !window.FPN) return 0;
      var data = window.FPN.launchData;
      if (data === undefined || data === null) return 0;
      if (typeof data !== 'string') data = JSON.stringify(data); // tolerate an object (seed should still be a string)
      var size = lengthBytesUTF8(data) + 1;
      var buffer = _malloc(size);
      stringToUTF8(data, buffer, size);
      return buffer;
    } catch (e) {
      if (typeof console !== 'undefined') console.warn('[FPN] FPN_GetLaunchData failed', e);
      return 0;
    }
  },

  FPN_SubmitScore: function (score, durationMs, eventLogPtr) {
    try {
      var eventLog = eventLogPtr ? UTF8ToString(eventLogPtr) : '[]';
      if (typeof window !== 'undefined' && window.FPN && typeof window.FPN.submitScore === 'function') {
        window.FPN.submitScore(score, durationMs, eventLog);
      } else if (typeof console !== 'undefined') {
        console.warn('[FPN] window.FPN.submitScore missing; score not sent', score, durationMs);
      }
    } catch (e) {
      if (typeof console !== 'undefined') console.error('[FPN] submitScore threw', e);
    }
  },

  FPN_Quit: function () {
    try {
      if (typeof window !== 'undefined' && window.FPN && typeof window.FPN.quit === 'function') {
        window.FPN.quit();
      }
    } catch (e) {
      if (typeof console !== 'undefined') console.error('[FPN] quit threw', e);
    }
  },

  FPN_Log: function (msgPtr) {
    try {
      if (typeof window !== 'undefined' && window.FPN && typeof window.FPN.log === 'function') {
        window.FPN.log(msgPtr ? UTF8ToString(msgPtr) : '');
      }
    } catch (e) {
      // logging must never break the game
    }
  }
});
