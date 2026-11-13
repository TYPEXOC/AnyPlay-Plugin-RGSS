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
    return response ? response.value : null;
  }

  function page(values, query, offset, limit, nameOf, idOf, include) {
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
    isReady: function () { return !!invoke("isReady"); },
    getState: function () { return invoke("getState") || {}; },
    listCapabilities: function () { return invoke("listCapabilities") || { engine: "unknown", toggles: [], values: [], features: [], actorValues: [] }; },
    setFlag: function (name, enabled) { return invoke("setFlag", [name, !!enabled]); },
    setSpeed: function (value) { return invoke("setSpeed", [value]); },
    setGameSpeed: function (value) { return invoke("setGameSpeed", [value]); },
    setRewardMultiplier: function (name, value) { return invoke("setRewardMultiplier", [name, value]); },
    getGold: function () { return Number(invoke("getGold") || 0); },
    getCoins: function () { return Number(invoke("getCoins") || 0); },
    setGold: function (value) { return invoke("setGold", [value]); },
    setCoins: function (value) { return invoke("setCoins", [value]); },
    listDatabase: function (kind, query, offset, limit, category) {
      var values = invoke("listDatabase", [kind, query, offset, limit, category || ""]);
      return values || { total: 0, values: [] };
    },
    listBagCategories: function () { return invoke("listBagCategories") || []; },
    give: function (kind, key, amount) { return invoke("give", [kind, key, amount]); },
    itemCount: function (kind, id) { return invoke("itemCount", [kind, id]); },
    setItemCount: function (kind, id, value) { return invoke("setItemCount", [kind, id, value]); },
    setItemLock: function (kind, id, locked) { return invoke("setItemLock", [kind, id, !!locked]); },
    listActors: function () { return invoke("listActors") || []; },
    setActorStat: function (id, key, value) { return invoke("setActorStat", [id, key, value]); },
    actorClasses: function (id) { return invoke("actorClasses", [id]) || { currentId: 0, classes: [] }; },
    setActorClass: function (id, classId) { return invoke("setActorClass", [id, classId]); },
    recoverActor: function (id) { return invoke("recoverActor", [id]); },
    setActorParty: function (id, inParty) { return invoke("setActorParty", [id, !!inParty]); },
    listPokemon: function () { return invoke("listPokemon") || []; },
    setPokemonValue: function (index, field, value) { return invoke("setPokemonValue", [index, field, value]); },
    recoverPokemon: function (index) { return invoke("recoverPokemon", [index]); },
    listPokemonMoves: function (index) { return invoke("listPokemonMoves", [index]) || []; },
    listPokemonTraits: function (index) { return invoke("listPokemonTraits", [index]) || []; },
    listPokemonMoveCandidates: function () { return invoke("listPokemonMoveCandidates") || []; },
    listPokemonStatuses: function (index) { return invoke("listPokemonStatuses", [index]) || []; },
    listPokemonStats: function (index) { return invoke("listPokemonStats", [index]) || { ivs: [], evs: [] }; },
    setPokemonMove: function (index, slot, key) { return invoke("setPokemonMove", [index, slot, key]); },
    setPokemonMovePp: function (index, slot, field, value) { return invoke("setPokemonMovePp", [index, slot, field, value]); },
    setPokemonTrait: function (index, field, key) { return invoke("setPokemonTrait", [index, field, key]); },
    setPokemonOrder: function (index, target) { return invoke("setPokemonOrder", [index, target]); },
    setPokemonStatus: function (index, key) { return invoke("setPokemonStatus", [index, key]); },
    setPokemonItem: function (index, key) { return invoke("setPokemonItem", [index, key]); },
    recoverParty: function () { return invoke("recoverParty"); },
    actorSkills: function (id) { return invoke("actorSkills", [id]) || []; },
    learnSkill: function (id, skillId) { return invoke("learnSkill", [id, skillId]); },
    forgetSkill: function (id, skillId) { return invoke("forgetSkill", [id, skillId]); },
    actorStates: function (id) { return invoke("actorStates", [id]) || []; },
    addState: function (id, stateId) { return invoke("addState", [id, stateId]); },
    removeState: function (id, stateId) { return invoke("removeState", [id, stateId]); },
    actorEquips: function (id) { return invoke("actorEquips", [id]) || []; },
    equipCandidates: function (id, slot) { return invoke("equipCandidates", [id, slot]) || []; },
    changeEquip: function (id, slot, kind, itemId) { return invoke("changeEquip", [id, slot, kind, itemId]); },
    listSwitches: function (query, offset, limit, includeUnnamed) {
      return page(invoke("listSystem", ["switch"]) || [], query, offset, limit,
        function (entry) { return entry.name; }, function (entry) { return entry.id; },
        function (entry) { return includeUnnamed || entry.named !== false; });
    },
    setSwitch: function (id, value) { return invoke("setSwitch", [id, !!value]); },
    setSystemLock: function (kind, id, locked) { return invoke("setSystemLock", [kind, id, !!locked]); },
    listVariables: function (query, offset, limit, includeUnnamed) {
      return page(invoke("listSystem", ["variable"]) || [], query, offset, limit,
        function (entry) { return entry.name; }, function (entry) { return entry.id; },
        function (entry) { return includeUnnamed || entry.named !== false; });
    },
    setVariable: function (id, value) { return invoke("setVariable", [id, value]); },
    listMaps: function (query, offset, limit) {
      return invoke("listMaps", [query, offset, limit]) || { total: 0, values: [] };
    },
    currentPosition: function () { return invoke("currentPosition"); },
    teleport: function (mapId, x, y, direction, fade) {
      return invoke("teleport", [mapId, x, y, direction, fade]);
    },
    minimapData: function () { return invoke("minimapData"); },
    listEvents: function (mapId) {
      var values = invoke("listEvents", mapId == null ? [] : [mapId]) || [];
      return { total: values.length, values: values };
    },
    eventDetail: function (eventId, mapId) { return invoke("eventDetail", [eventId, mapId]); },
    eventInterpreter: function (eventId, mapId, pageIndex) {
      return invoke("eventInterpreter", [eventId, mapId, pageIndex]) || [];
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
    battleSnapshot: function () { return invoke("battleSnapshot"); },
    setEnemyHp: function (index, hp) { return invoke("setEnemyHp", [index, hp]); },
    killAllEnemies: function () { return invoke("killAllEnemies"); },
    forceVictory: function () { return invoke("forceVictory"); },
    forceDefeat: function () { return invoke("forceDefeat"); },
    forceEscape: function () { return invoke("forceEscape"); },
    listSaveSlots: function () { return invoke("listSaveSlots") || []; },
    saveGame: function (id) { return invoke("saveGame", [id]); },
    loadGame: function (id) { return invoke("loadGame", [id]); },
    deleteSave: function (id) { return invoke("deleteSave", [id]); }
  };

  window.TyranorMod = mod;
})();
