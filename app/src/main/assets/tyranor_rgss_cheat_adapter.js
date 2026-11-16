/*
 * RGSS cheat UI API adapter for the RGSS Ruby controller.
 * The Web UI calls this adapter, which translates the
 * UI's MV/MZ runtime calls into the RGSS module's existing controller API.
 */
(function () {
  "use strict";

  try {
    window.TyranorModLanguageTag = window.TyranorCheatNative &&
      window.TyranorCheatNative.uiLanguageTag ? window.TyranorCheatNative.uiLanguageTag() : "";
  } catch (_) {
    window.TyranorModLanguageTag = "";
  }

  function invoke(method, args) {
    var bridge = window.TyranorCheatNative;
    if (!bridge) throw new Error("RGSS 修改器桥未连接");
    var response;
    try {
      response = JSON.parse(bridge.invoke(method, JSON.stringify(args || [])));
    } catch (error) {
      throw new Error(error && error.message ? error.message : String(error));
    }
    if (response && response.__error) throw new Error(response.__error);
    var value = response ? response.value : null;
    invalidateAfterMutation(method, args || []);
    return value;
  }

  var asyncRequestSequence = 0;
  var asyncRequests = Object.create(null);
  var readCache = Object.create(null);
  var refreshNotificationPending = false;
  var refreshNotificationChanges = [];
  var refreshNotificationUnknown = false;

  window.TyranorModBridgeResponse = function (requestId, responseJson) {
    var pending = asyncRequests[requestId];
    if (!pending) return;
    delete asyncRequests[requestId];
    window.clearTimeout(pending.timeout);
    try {
      var response = JSON.parse(responseJson);
      if (response && response.__error) pending.reject(new Error(response.__error));
      else pending.resolve(response ? response.value : null);
    } catch (error) {
      pending.reject(error);
    }
  };

  function invokeAsync(method, args) {
    return new Promise(function (resolve, reject) {
      var bridge = window.TyranorCheatNative;
      if (!bridge || typeof bridge.invokeAsync !== "function") {
        reject(new Error("RGSS 异步修改器桥未连接"));
        return;
      }
      var requestId = Date.now().toString(36) + "-" + (++asyncRequestSequence).toString(36);
      var timeout = window.setTimeout(function () {
        var pending = asyncRequests[requestId];
        if (!pending) return;
        delete asyncRequests[requestId];
        reject(new Error("RGSS 游戏没有及时响应这项查询"));
      }, 40000);
      asyncRequests[requestId] = { resolve: resolve, reject: reject, timeout: timeout };
      try {
        bridge.invokeAsync(requestId, method, JSON.stringify(args || []));
      } catch (error) {
        delete asyncRequests[requestId];
        window.clearTimeout(timeout);
        reject(error);
      }
    });
  }

  function loadingValue(fallback) {
    if (Array.isArray(fallback)) {
      var list = [];
      list.loading = true;
      return list;
    }
    if (fallback && typeof fallback === "object") {
      var value = Object.assign({}, fallback);
      value.loading = true;
      return value;
    }
    return fallback;
  }

  function notifyAsyncDataUpdated(change) {
    if (change) refreshNotificationChanges.push(change);
    else refreshNotificationUnknown = true;
    if (refreshNotificationPending) return;
    refreshNotificationPending = true;
    window.setTimeout(function () {
      refreshNotificationPending = false;
      var changes = refreshNotificationChanges;
      var unknown = refreshNotificationUnknown;
      refreshNotificationChanges = [];
      refreshNotificationUnknown = false;
      var ui = window.TyranorModUI;
      if (ui && typeof ui.onAsyncDataUpdated === "function") ui.onAsyncDataUpdated(changes, unknown);
    }, 16);
  }

  function cachedRead(method, args, fallback, ttlMs, transform) {
    var key = method + "|" + JSON.stringify(args || []);
    var now = Date.now();
    var entry = readCache[key];
    if (!entry) {
      entry = readCache[key] = { method: method, value: null, hasValue: false, pending: false, expiresAt: 0, retryAt: 0, version: 0, invalidated: false, lastUsedAt: now };
      trimReadCache();
    }
    entry.lastUsedAt = now;
    if (!entry.pending && now >= entry.expiresAt && now >= entry.retryAt) {
      entry.pending = true;
      var requestVersion = entry.version;
      invokeAsync(method, args).then(function (value) {
        return transform ? transform(value) : value;
      }).then(function (value) {
        entry.pending = false;
        if (entry.version !== requestVersion) {
          entry.expiresAt = 0;
          entry.retryAt = 0;
          entry.invalidated = true;
          notifyAsyncDataUpdated({ method: method, args: args || [] });
          return;
        }
        entry.hasValue = true;
        entry.value = value;
        entry.invalidated = false;
        entry.expiresAt = Date.now() + Math.max(0, ttlMs || 0);
        entry.retryAt = 0;
        notifyAsyncDataUpdated({ method: method, args: args || [] });
      }).catch(function (error) {
        entry.pending = false;
        var staleRequest = entry.version !== requestVersion;
        entry.retryAt = staleRequest ? 0 : Date.now() + 2000;
        if (staleRequest) {
          entry.expiresAt = 0;
          entry.invalidated = true;
        } else {
          entry.invalidated = false;
        }
        console.warn("RGSS async query failed: " + method, error);
        notifyAsyncDataUpdated({ method: method, args: args || [] });
        if (!staleRequest) window.setTimeout(function () {
          notifyAsyncDataUpdated({ method: method, args: args || [] });
        }, 2000);
      });
    }
    return entry.hasValue && !entry.invalidated ? entry.value : loadingValue(fallback);
  }

  function cachedReadSnapshot(method, args, fallback, ttlMs) {
    cachedRead(method, args, fallback, ttlMs);
    var key = method + "|" + JSON.stringify(args || []);
    var entry = readCache[key];
    return {
      value: entry && entry.hasValue && !entry.invalidated ? entry.value : null,
      loading: !entry || entry.pending || !entry.hasValue || entry.invalidated
    };
  }

  function invalidateMethods(methods) {
    var affected = Object.create(null);
    methods.forEach(function (method) { affected[method] = true; });
    Object.keys(readCache).forEach(function (key) {
      var entry = readCache[key];
      if (affected[entry.method]) {
        entry.version += 1;
        entry.expiresAt = 0;
        entry.invalidated = true;
      }
    });
  }

  function trimReadCache() {
    var keys = Object.keys(readCache);
    if (keys.length <= 300) return;
    keys.sort(function (left, right) {
      return (readCache[left].lastUsedAt || 0) - (readCache[right].lastUsedAt || 0);
    });
    for (var index = 0; index < keys.length && Object.keys(readCache).length > 240; index++) {
      if (!readCache[keys[index]].pending) delete readCache[keys[index]];
    }
  }

  function invalidateAfterMutation(method, args) {
    var affected = {
      setFlag: ["getState"], setSpeed: ["getState"], setGameSpeed: ["getState"], setRewardMultiplier: ["getState"],
      setGold: ["getGold"], setCoins: ["getCoins"],
      give: ["listDatabase", "itemCount", "listBagCategories"],
      setItemCount: ["listDatabase", "itemCount"], setItemLock: ["listDatabase"],
      setActorStat: ["listActors", "battleSnapshot"], setActorClass: ["listActors", "actorClasses"],
      setActorParty: ["listActors"], recoverActor: ["listActors", "actorSkills", "actorStates", "actorEquips"],
      learnSkill: ["actorSkills", "listActors"], forgetSkill: ["actorSkills", "listActors"],
      addState: ["actorStates", "listActors"], removeState: ["actorStates", "listActors"],
      changeEquip: ["actorEquips", "equipCandidates", "listActors"],
      setPokemonValue: ["listPokemon", "listPokemonStats"], setPokemonNickname: ["listPokemon"],
      movePokemonToStorage: ["listPokemon", "listPokemonStorage", "listCapabilities"], withdrawPokemonFromStorage: ["listPokemon", "listPokemonStorage", "listCapabilities"],
      releasePokemon: ["listPokemon", "listPokemonStorage", "listCapabilities"], setPokemonProgress: ["listPokemonProgress"],
      recoverPokemon: ["listPokemon", "listPokemonMoves", "listPokemonStatuses"],
      setPokemonMove: ["listPokemonMoves", "listPokemon"], setPokemonMovePp: ["listPokemonMoves", "listPokemon"],
      setPokemonTrait: ["listPokemonTraits", "listPokemon"], setPokemonOrder: ["listPokemon"],
      setPokemonStatus: ["listPokemonStatuses", "listPokemon"], setPokemonItem: ["listPokemon", "listDatabase"],
      recoverParty: ["listActors", "listPokemon", "listPokemonMoves", "listPokemonStatuses"],
      setSwitch: ["listSystem", "eventDetail"], setVariable: ["listSystem", "eventDetail"], setSystemLock: ["listSystem"],
      teleport: ["currentPosition", "minimapData", "listEvents"],
      setEventSelfSwitch: ["listEvents", "eventDetail", "minimapData"],
      setEventSelfSwitches: ["listEvents", "eventDetail", "minimapData"],
      setMapEventSelfSwitches: ["listEvents", "eventDetail", "minimapData"],
      setEnemyHp: ["battleSnapshot"], setBattleUnitHp: ["battleSnapshot"], setEnemyMp: ["battleSnapshot"], battleAction: ["battleSnapshot"], killAllEnemies: ["battleSnapshot"],
      forceVictory: ["battleSnapshot"], forceDefeat: ["battleSnapshot"], forceEscape: ["battleSnapshot"],
      saveGame: ["listSaveSlots"], loadGame: ["isReady", "getState", "listSaveSlots", "getGold", "getCoins", "listDatabase", "itemCount", "listBagCategories", "listActors", "actorClasses", "actorSkills", "actorStates", "actorEquips", "listPokemon", "listPokemonStorage", "listPokemonProgress", "listPokemonMoves", "listPokemonTraits", "listPokemonStatuses", "listPokemonStats", "listCapabilities", "listSystem", "currentPosition", "listEvents", "eventDetail", "eventInterpreter", "battleSnapshot", "minimapData"],
      deleteSave: ["listSaveSlots"]
    }[method];
    if (!affected) return;
    if (method === "give" && String(args[0] || "").toLowerCase() === "pokemon") {
      affected.push("listPokemon", "listPokemonStorage", "listCapabilities");
    }
    invalidateMethods(affected);
    notifyAsyncDataUpdated();
  }

  function refreshReads() {
    Object.keys(readCache).forEach(function (key) {
      var entry = readCache[key];
      entry.version += 1;
      entry.expiresAt = 0;
      entry.retryAt = 0;
      entry.invalidated = true;
    });
    notifyAsyncDataUpdated();
  }

  function page(values, query, offset, limit, nameOf, idOf, include) {
    if (values && values.loading) return { total: 0, values: [], loading: true };
    query = String(query || "").toLowerCase();
    offset = Math.max(0, Number(offset) || 0);
    limit = Math.max(1, Math.min(300, Number(limit) || 100));
    var filtered = values.filter(function (value) {
      return (!include || include(value)) && (!query || String(nameOf(value) || "").toLowerCase().indexOf(query) >= 0 ||
        String(idOf(value)).toLowerCase().indexOf(query) >= 0);
    });
    return { total: filtered.length, values: filtered.slice(offset, offset + limit) };
  }

  var mod = {
    version: "1.5.1",
    launchEngine: (function () {
      try {
        var bridge = window.TyranorCheatNative;
        var engine = bridge && bridge.launchEngine ? String(bridge.launchEngine()) : "unknown";
        return /^(xp|vx|ace|essentials)$/.test(engine) ? engine : "unknown";
      } catch (_) { return "unknown"; }
    })(),
    isReady: function () { return !!invoke("isReady"); },
    isReadySnapshot: function () { return cachedReadSnapshot("isReady", [], false, 1000); },
    getState: function () { return invoke("getState") || {}; },
    getStateSnapshot: function () { return cachedReadSnapshot("getState", [], {}, 5000); },
    refreshReads: refreshReads,
    listCapabilities: function () { return cachedRead("listCapabilities", [], { engine: "unknown", toggles: [], values: [], features: [], actorValues: [] }, 1500); },
    setFlag: function (name, enabled) { return invoke("setFlag", [name, !!enabled]); },
    setSpeed: function (value) { return invoke("setSpeed", [value]); },
    setGameSpeed: function (value) { return invoke("setGameSpeed", [value]); },
    setRewardMultiplier: function (name, value) { return invoke("setRewardMultiplier", [name, value]); },
    getGold: function () { return Number(cachedRead("getGold", [], 0, 1000) || 0); },
    getCoins: function () { return Number(cachedRead("getCoins", [], 0, 1000) || 0); },
    getGoldSnapshot: function () { return cachedReadSnapshot("getGold", [], 0, 1000); },
    getCoinsSnapshot: function () { return cachedReadSnapshot("getCoins", [], 0, 1000); },
    setGold: function (value) { return invoke("setGold", [value]); },
    setCoins: function (value) { return invoke("setCoins", [value]); },
    listDatabase: function (kind, query, offset, limit, category) {
      var values = cachedRead("listDatabase", [kind, query, offset, limit, category || ""], { total: 0, values: [] }, 2000);
      return values || { total: 0, values: [] };
    },
    listBagCategories: function () { return cachedRead("listBagCategories", [], [], 30000) || []; },
    give: function (kind, key, amount) { return invoke("give", [kind, key, amount]); },
    itemCount: function (kind, id) { return cachedRead("itemCount", [kind, id], 0, 1000); },
    setItemCount: function (kind, id, value) { return invoke("setItemCount", [kind, id, value]); },
    setItemLock: function (kind, id, locked) { return invoke("setItemLock", [kind, id, !!locked]); },
    listActors: function () { return cachedRead("listActors", [], [], 1000) || []; },
    setActorStat: function (id, key, value) { return invoke("setActorStat", [id, key, value]); },
    actorClasses: function (id) { return cachedRead("actorClasses", [id], { currentId: 0, classes: [] }, 30000); },
    setActorClass: function (id, classId) { return invoke("setActorClass", [id, classId]); },
    recoverActor: function (id) { return invoke("recoverActor", [id]); },
    setActorParty: function (id, inParty) { return invoke("setActorParty", [id, !!inParty]); },
    listPokemon: function () { return cachedRead("listPokemon", [], [], 1000) || []; },
    listPokemonStorage: function (box) { return cachedRead("listPokemonStorage", [box], { boxIndex: box, boxCount: 0, name: "", pokemon: [] }, 1000); },
    listPokemonProgress: function () { return cachedRead("listPokemonProgress", [], { badges: [], dexes: [] }, 30000); },
    setPokemonValue: function (index, field, value) { return invoke("setPokemonValue", [index, field, value]); },
    setPokemonNickname: function (index, nickname) { return invoke("setPokemonNickname", [index, nickname]); },
    movePokemonToStorage: function (index, box) { return invoke("movePokemonToStorage", [index, box]); },
    withdrawPokemonFromStorage: function (box, slot) { return invoke("withdrawPokemonFromStorage", [box, slot]); },
    releasePokemon: function (box, slot) { return invoke("releasePokemon", [box, slot]); },
    setPokemonProgress: function (kind, index, obtained) { return invoke("setPokemonProgress", [kind, index, !!obtained]); },
    recoverPokemon: function (index) { return invoke("recoverPokemon", [index]); },
    listPokemonMoves: function (index) { return cachedRead("listPokemonMoves", [index], [], 1000) || []; },
    listPokemonTraits: function (index) { return cachedRead("listPokemonTraits", [index], [], 30000) || []; },
    listPokemonMoveCandidates: function () { return cachedRead("listPokemonMoveCandidates", [], [], 30000) || []; },
    listPokemonStatuses: function (index) { return cachedRead("listPokemonStatuses", [index], [], 1000) || []; },
    listPokemonStats: function (index) { return cachedRead("listPokemonStats", [index], { ivs: [], evs: [] }, 1000); },
    setPokemonMove: function (index, slot, key) { return invoke("setPokemonMove", [index, slot, key]); },
    setPokemonMovePp: function (index, slot, field, value) { return invoke("setPokemonMovePp", [index, slot, field, value]); },
    setPokemonTrait: function (index, field, key) { return invoke("setPokemonTrait", [index, field, key]); },
    setPokemonOrder: function (index, target) { return invoke("setPokemonOrder", [index, target]); },
    setPokemonStatus: function (index, key) { return invoke("setPokemonStatus", [index, key]); },
    setPokemonItem: function (index, key) { return invoke("setPokemonItem", [index, key]); },
    recoverParty: function () { return invoke("recoverParty"); },
    actorSkills: function (id) { return cachedRead("actorSkills", [id], [], 1000) || []; },
    learnSkill: function (id, skillId) { return invoke("learnSkill", [id, skillId]); },
    forgetSkill: function (id, skillId) { return invoke("forgetSkill", [id, skillId]); },
    actorStates: function (id) { return cachedRead("actorStates", [id], [], 1000) || []; },
    addState: function (id, stateId) { return invoke("addState", [id, stateId]); },
    removeState: function (id, stateId) { return invoke("removeState", [id, stateId]); },
    actorEquips: function (id) { return cachedRead("actorEquips", [id], [], 1000) || []; },
    equipCandidates: function (id, slot) { return cachedRead("equipCandidates", [id, slot], [], 30000) || []; },
    changeEquip: function (id, slot, kind, itemId) { return invoke("changeEquip", [id, slot, kind, itemId]); },
    listSwitches: function (query, offset, limit, includeUnnamed) {
      return page(cachedRead("listSystem", ["switch"], [], 5000) || [], query, offset, limit,
        function (entry) { return entry.name; }, function (entry) { return entry.id; },
        function (entry) { return includeUnnamed || entry.named !== false; });
    },
    setSwitch: function (id, value) { return invoke("setSwitch", [id, !!value]); },
    setSystemLock: function (kind, id, locked) { return invoke("setSystemLock", [kind, id, !!locked]); },
    listVariables: function (query, offset, limit, includeUnnamed) {
      return page(cachedRead("listSystem", ["variable"], [], 5000) || [], query, offset, limit,
        function (entry) { return entry.name; }, function (entry) { return entry.id; },
        function (entry) { return includeUnnamed || entry.named !== false; });
    },
    setVariable: function (id, value) { return invoke("setVariable", [id, value]); },
    listMaps: function (query, offset, limit) {
      var catalog = cachedRead("listMaps", ["", 0, 300], { total: 0, values: [] }, 600000);
      if (catalog.loading) return catalog;
      var position = cachedRead("currentPosition", [], { mapId: 0, x: 0, y: 0, direction: 2 }, 5000);
      var maps = (catalog.values || []).map(function (entry) {
        if (!position || position.loading || !Number(position.mapId)) return entry;
        var current = Number(entry.id) === Number(position.mapId);
        return Object.assign({}, entry, {
          current: current,
          currentX: current ? position.x : "",
          currentY: current ? position.y : ""
        });
      });
      return page(maps, query, offset, limit,
        function (entry) { return entry.name; }, function (entry) { return entry.id; });
    },
    currentPosition: function () { return cachedRead("currentPosition", [], { mapId: 0, x: 0, y: 0, direction: 2 }, 5000); },
    teleport: function (mapId, x, y, direction, fade) {
      return invoke("teleport", [mapId, x, y, direction, fade]);
    },
    minimapData: function (mapId) {
      var currentPosition = cachedRead("currentPosition", [], { mapId: 0 }, 5000);
      var isCurrentMap = currentPosition && Number(currentPosition.mapId) === Number(mapId);
      return cachedRead("minimapData", mapId == null ? [] : [mapId], { mapId: Number(mapId) || 0, width: 0, height: 0, rows: [], player: { x: -1, y: -1 }, events: [] }, isCurrentMap ? 5000 : 300000);
    },
    listEvents: function (mapId) {
      var currentPosition = cachedRead("currentPosition", [], { mapId: 0 }, 5000);
      var isCurrentMap = currentPosition && Number(currentPosition.mapId) === Number(mapId);
      var values = cachedRead("listEvents", mapId == null ? [] : [mapId], [], isCurrentMap ? 30000 : 300000) || [];
      return { total: values.length, values: values, loading: !!values.loading };
    },
    eventDetail: function (eventId, mapId) { return cachedRead("eventDetail", [eventId, mapId], { loading: true }, 5000); },
    eventInterpreter: function (eventId, mapId, pageIndex) {
      return cachedRead("eventInterpreter", [eventId, mapId, pageIndex], [], 5000) || [];
    },
    setEventSelfSwitch: function (eventId, letter, enabled, mapId) {
      return invoke("setEventSelfSwitch", [eventId, letter, !!enabled, mapId]);
    },
    setEventSelfSwitches: function (eventId, enabled, mapId) {
      return invoke("setEventSelfSwitches", [eventId, !!enabled, mapId]);
    },
    setMapEventSelfSwitches: function (mapId, enabled) {
      return invoke("setMapEventSelfSwitches", [mapId, !!enabled]);
    },
    battleSnapshot: function () { return cachedRead("battleSnapshot", [], { inBattle: false, actors: [], enemies: [] }, 500); },
    setEnemyHp: function (index, hp) { return invoke("setEnemyHp", [index, hp]); },
    setBattleUnitHp: function (side, index, hp) { return invoke("setBattleUnitHp", [side, index, hp]); },
    setEnemyMp: function (index, mp) { return invoke("setEnemyMp", [index, mp]); },
    battleAction: function (side, operation, index) { return invoke("battleAction", [side, operation, index == null ? -1 : index]); },
    killAllEnemies: function () { return invoke("killAllEnemies"); },
    forceVictory: function () { return invoke("forceVictory"); },
    forceDefeat: function () { return invoke("forceDefeat"); },
    forceEscape: function () { return invoke("forceEscape"); },
    openGameScene: function (scene) { return invoke("openGameScene", [scene]); },
    listSaveSlots: function () { return cachedRead("listSaveSlots", [], [], 5000) || []; },
    saveGame: function (id) { return invoke("saveGame", [id]); },
    loadGame: function (id) { return invoke("loadGame", [id]); },
    deleteSave: function (id) { return invoke("deleteSave", [id]); }
  };

  window.TyranorMod = mod;
})();
