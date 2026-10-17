/*
 * FunPixelNest — Unity WebGL game host for the web app.
 *
 * Reads <gamesBaseUrl>catalog.json, e.g.
 *   {
 *     "devControls": true,                       // shows a "test finish" button (turn off for production)
 *     "games": {
 *       "solitaire": {
 *         "path": "solitaire/v1/",               // folder of this build, relative to gamesBaseUrl
 *         "loaderUrl": "Build/Solitaire.loader.js",
 *         "dataUrl": "Build/Solitaire.data.unityweb",
 *         "frameworkUrl": "Build/Solitaire.framework.js.unityweb",
 *         "codeUrl": "Build/Solitaire.wasm.unityweb",
 *         "streamingAssetsUrl": "StreamingAssets",
 *         "companyName": "FunPixelNest", "productName": "Solitaire", "productVersion": "1.0"
 *       }
 *     }
 *   }
 *
 * While a game runs, window.FPN is the bridge the FunPixelNest Unity SDK talks to:
 *   FPN.launchData (JSON string) · FPN.submitScore(score, durationMs, eventLogJson) · FPN.quit() · FPN.log(msg)
 */
(function () {
  "use strict";

  var STYLE = [
    "#fpn-game{position:fixed;inset:0;z-index:2147483000;display:flex;flex-direction:column;background:#0e1a33;",
    "font-family:Nunito,system-ui,-apple-system,Segoe UI,Roboto,sans-serif;color:#fff}",
    "#fpn-game .fpn-bar{display:flex;align-items:center;gap:10px;flex:0 0 auto;",
    "padding:calc(8px + env(safe-area-inset-top,0px)) max(12px,env(safe-area-inset-right,0px)) 8px max(12px,env(safe-area-inset-left,0px));",
    "background:#1d2b4f}",
    "#fpn-game .fpn-title{flex:1 1 auto;min-width:0;font-weight:800;font-size:16px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}",
    "#fpn-game .fpn-tag{font-size:12px;font-weight:700;background:#ffc93c;color:#1d2b4f;border-radius:99px;padding:2px 10px}",
    "#fpn-game button{font:inherit;font-weight:800;border:0;border-radius:99px;padding:8px 16px;cursor:pointer}",
    "#fpn-game button:focus-visible{outline:3px solid #ffc93c;outline-offset:2px}",
    "#fpn-game .fpn-exit{background:#f26b1d;color:#fff}",
    "#fpn-game .fpn-dev{background:#2b86f0;color:#fff;font-size:12px;padding:6px 12px}",
    "#fpn-game .fpn-stage{position:relative;flex:1 1 auto;min-height:0}",
    "#fpn-game canvas{display:block;width:100%;height:100%;background:#000;outline:none}",
    "#fpn-game .fpn-cover{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;",
    "gap:14px;padding:24px;text-align:center;background:#0e1a33}",
    "#fpn-game .fpn-track{width:min(280px,70vw);height:10px;border-radius:99px;background:#2a3a63;overflow:hidden}",
    "#fpn-game .fpn-fill{height:100%;width:0;background:#ffc93c;transition:width .2s}",
    "#fpn-game .fpn-muted{color:#b9c6e4;font-size:14px;max-width:340px}",
    "#fpn-game .fpn-row{display:flex;gap:10px;flex-wrap:wrap;justify-content:center}",
    "#fpn-game .fpn-ghost{background:#2a3a63;color:#fff}",
  ].join("");

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  function joinUrl(base, path) {
    if (!path) return base;
    if (/^(https?:)?\/\//.test(path) || path.charAt(0) === "/") return path;
    return base + path;
  }

  var host = {
    base: "games/",
    catalog: null,
    catalogPromise: null,
    loadedLoaders: {},
    active: null,

    init: function (base) {
      this.base = !base ? "" : base.charAt(base.length - 1) === "/" ? base : base + "/";
      this.loadCatalog();
    },

    loadCatalog: function () {
      var self = this;
      if (!this.catalogPromise) {
        this.catalogPromise = fetch(this.base + "catalog.json", { cache: "no-cache" })
          .then(function (r) { return r.ok ? r.json() : { games: {} }; })
          .catch(function () { return { games: {} }; })
          .then(function (c) {
            self.catalog = c && c.games ? c : { games: {} };
            return self.catalog;
          });
      }
      return this.catalogPromise;
    },

    has: function (gameId) {
      return !!(this.catalog && this.catalog.games && this.catalog.games[gameId]);
    },

    launch: function (gameId, gameName, launchJson, onDone) {
      var self = this;
      if (this.active) { onDone(""); return; }
      var entry = this.catalog && this.catalog.games[gameId];
      if (!entry) { onDone(""); return; }

      var launch = {};
      try { launch = JSON.parse(launchJson); } catch (e) { /* keep empty */ }
      var isPaid = launch.mode && launch.mode !== "practice";
      var buildBase = joinUrl(this.base, entry.path || "");

      if (!document.getElementById("fpn-game-style")) {
        var style = el("style");
        style.id = "fpn-game-style";
        style.textContent = STYLE;
        document.head.appendChild(style);
      }

      // ---- overlay UI
      var root = el("div"); root.id = "fpn-game";
      var bar = el("div", "fpn-bar");
      var title = el("div", "fpn-title", gameName || gameId);
      var tag = el("span", "fpn-tag", launch.mode === "duel" ? "1v1" : isPaid ? "Tournament" : "Practice");
      var exitBtn = el("button", "fpn-exit", "Exit");
      exitBtn.type = "button";
      bar.appendChild(title); bar.appendChild(tag);
      if (this.catalog.devControls) {
        var dev = el("button", "fpn-dev", "Test finish");
        dev.type = "button";
        dev.title = "Developer test: finish with a random score";
        dev.onclick = function () {
          finish({ score: 1000 + Math.floor(Math.random() * 9000), durationMs: 60000 + Math.floor(Math.random() * 180000), eventLog: "[]" });
        };
        bar.appendChild(dev);
      }
      bar.appendChild(exitBtn);

      var stage = el("div", "fpn-stage");
      var canvas = el("canvas"); canvas.id = "fpn-unity-canvas"; canvas.tabIndex = -1;
      var cover = el("div", "fpn-cover");
      var loadingText = el("div", null, "Loading " + (gameName || "game") + "…");
      loadingText.style.fontWeight = "800";
      var track = el("div", "fpn-track"); var fill = el("div", "fpn-fill"); track.appendChild(fill);
      cover.appendChild(loadingText); cover.appendChild(track);
      stage.appendChild(canvas); stage.appendChild(cover);
      root.appendChild(bar); root.appendChild(stage);
      document.body.appendChild(root);

      var done = false;
      var instance = null;

      function teardown(resultJson) {
        if (done) return;
        done = true;
        window.FPN = undefined;
        var finishUp = function () {
          if (root.parentNode) root.parentNode.removeChild(root);
          self.active = null;
          onDone(resultJson);
        };
        if (instance && typeof instance.Quit === "function") {
          instance.Quit().then(finishUp, finishUp);
        } else {
          finishUp();
        }
      }

      function finish(result) {
        teardown(result ? JSON.stringify(result) : "");
      }

      // ---- the bridge the Unity SDK uses
      window.FPN = {
        launchData: launchJson,
        submitScore: function (score, durationMs, eventLogJson) {
          finish({ score: Number(score) || 0, durationMs: Number(durationMs) || 0, eventLog: eventLogJson || "[]" });
        },
        quit: function () { finish(null); },
        log: function (message) { console.log("[FunPixelNest game]", message); },
      };

      function showMessage(text, buttons) {
        cover.innerHTML = "";
        cover.style.display = "flex";
        cover.appendChild(el("div", "fpn-muted", text));
        var row = el("div", "fpn-row");
        buttons.forEach(function (b) {
          var btn = el("button", b.cls, b.label); btn.type = "button"; btn.onclick = b.onClick; row.appendChild(btn);
        });
        cover.appendChild(row);
      }

      exitBtn.onclick = function () {
        if (!isPaid) { finish(null); return; }
        var wasLoading = !instance;
        showMessage("Leave this match? Your entry is already paid, so leaving counts as a score of 0.", [
          { label: "Keep playing", cls: "fpn-ghost", onClick: function () { cover.style.display = wasLoading ? "flex" : "none"; if (wasLoading) { cover.innerHTML = ""; cover.appendChild(loadingText); cover.appendChild(track); } } },
          { label: "Leave match", cls: "fpn-exit", onClick: function () { finish(null); } },
        ]);
      };

      // ---- load Unity
      var config = {
        dataUrl: joinUrl(buildBase, entry.dataUrl),
        frameworkUrl: joinUrl(buildBase, entry.frameworkUrl),
        codeUrl: joinUrl(buildBase, entry.codeUrl),
        streamingAssetsUrl: joinUrl(buildBase, entry.streamingAssetsUrl || "StreamingAssets"),
        companyName: entry.companyName || "FunPixelNest",
        productName: entry.productName || gameName || gameId,
        productVersion: entry.productVersion || "1.0",
        matchWebGLToCanvasSize: true,
        devicePixelRatio: Math.min(window.devicePixelRatio || 1, 2),
      };
      if (entry.symbolsUrl) config.symbolsUrl = joinUrl(buildBase, entry.symbolsUrl);
      if (entry.memoryUrl) config.memoryUrl = joinUrl(buildBase, entry.memoryUrl);

      var loaderUrl = joinUrl(buildBase, entry.loaderUrl);
      this.active = gameId;

      ensureLoader(loaderUrl)
        .then(function () {
          if (done) return null;
          return window.createUnityInstance(canvas, config, function (p) {
            fill.style.width = Math.round(Math.max(0, Math.min(1, p)) * 100) + "%";
          });
        })
        .then(function (inst) {
          if (!inst) return;
          instance = inst;
          if (done) { inst.Quit(); return; }
          cover.style.display = "none";
          canvas.focus();
        })
        .catch(function (err) {
          console.error("[FunPixelNest] Unity failed to load", err);
          if (done) return;
          showMessage("This game couldn't load. " + (isPaid ? "Leaving now counts as a score of 0." : ""), [
            { label: "Close", cls: "fpn-exit", onClick: function () { finish(null); } },
          ]);
        });

      function ensureLoader(url) {
        if (self.loadedLoaders[url]) return self.loadedLoaders[url];
        self.loadedLoaders[url] = new Promise(function (resolve, reject) {
          var s = document.createElement("script");
          s.src = url;
          s.onload = function () { resolve(); };
          s.onerror = function () { delete self.loadedLoaders[url]; reject(new Error("Could not load " + url)); };
          document.body.appendChild(s);
        });
        return self.loadedLoaders[url];
      }
    },
  };

  window.FPNHost = host;
})();
