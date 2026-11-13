(function () {
  "use strict";
  var mod = window.TyranorMod;
  var rpgUI = window.TyranorModUI;
  if (!mod || !rpgUI) return;

  var panel = null;
  var probe = null;
  var caps = null;
  var tab = "team";
  var detectTimer = null;
  var detectAttempts = 0;
  var featureAttempts = 0;
  var itemQuery = "";
  var itemOffset = 0;
  var showEmptyItems = false;
  var systemKind = "switch";
  var systemQuery = "";
  var systemOffset = 0;
  var showUnnamedSystem = true;
  var mapQuery = "";
  var mapId = 0;
  var selectedPokemon = 0;
  var moveContext = null;
  var pendingConfirmation = null;
  var statNames = ["HP", "攻击", "防御", "特攻", "特防", "速度"];
  var flagLabels = {
    hpmp: "HP/PP不减", status: "异常状态免疫", items: "背包物品不减", coins: "游戏币不减",
    noEncounter: "无野生遇敌", noclip: "穿墙", fastText: "快速文字", eventSpeed: "事件加速",
    msgSkip: "对话快进", invincible: "无敌", oneHit: "一击必杀", crit: "必定暴击", escape: "必定逃跑成功", freeSkill: "招式PP不减"
  };

  function tr(value) { return window.TyranorCheatTranslate ? window.TyranorCheatTranslate(value) : value; }
  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[c];
    });
  }
  function safeId(value) { return String(value).replace(/[^a-zA-Z0-9_-]/g, "_"); }
  function card(title, body, cls) {
    return '<section class="tm-card' + (cls ? " " + cls : "") + '"><h3>' + esc(tr(title)) + '</h3>' + body + '</section>';
  }
  function button(label, action, extra, data) {
    return '<button type="button" class="tm-btn ' + (extra || "") + '" data-action="' + action + '" ' + (data || "") + '>' + esc(tr(label)) + '</button>';
  }
  function input(id, value, type, attrs) {
    return '<input class="tm-input" id="' + id + '" type="' + (type || "text") + '" value="' + esc(value) + '" ' + (attrs || "") + '>';
  }
  function value(id) { var node = panel && panel.querySelector("#" + id); return node ? node.value : ""; }
  function toast(message, error) {
    if (!panel) return;
    var old = panel.querySelector(".tm-toast"); if (old) old.remove();
    var node = document.createElement("div"); node.className = "tm-toast";
    node.textContent = message || tr(error ? "操作失败" : "完成");
    if (error) node.style.background = "#8c1d18";
    panel.appendChild(node); setTimeout(function () { if (node.parentNode) node.remove(); }, 2400);
  }
  function run(action, onSuccess) {
    try { var result = action(); if (onSuccess) onSuccess(result); }
    catch (error) { console.error("Pokémon cheat action failed", error); toast(error.message || String(error), true); }
  }
  function getCaps() {
    caps = caps || {};
    caps.features = Array.isArray(caps.features) ? caps.features : [];
    caps.toggles = Array.isArray(caps.toggles) ? caps.toggles : [];
    caps.values = Array.isArray(caps.values) ? caps.values : [];
    return caps;
  }
  function hasFeature(name) { return getCaps().features.indexOf(name) >= 0; }
  function hasToggle(name) { return getCaps().toggles.indexOf(name) >= 0; }
  function hasValue(name) { return getCaps().values.indexOf(name) >= 0; }
  function visibleTabs() {
    var tabs = [["quick", "快捷操作"]];
    if (hasFeature("itemBag")) tabs.push(["bag", "背包"]);
    if (hasFeature("pokemonParty")) tabs.push(["team", "队伍"]);
    if (hasFeature("systemData")) tabs.push(["system", "开关/变量"]);
    if (hasFeature("mapControl") || hasFeature("events") || hasFeature("minimap")) tabs.push(["map", "地图/事件"]);
    if (hasFeature("battle")) tabs.push(["battle", "战斗"]);
    if (hasFeature("saveSlots")) tabs.push(["save", "存档"]);
    if (inputAvailable()) tabs.push(["keys", "键盘"]);
    tabs.push(["about", "说明"]);
    return tabs;
  }
  function renderTabs() {
    var tabs = visibleTabs();
    if (!tabs.some(function (entry) { return entry[0] === tab; })) tab = hasFeature("pokemonParty") ? "team" : "quick";
    panel.querySelector(".tm-tabs").innerHTML = tabs.map(function (entry) {
      return '<button class="tm-tab' + (entry[0] === tab ? " active" : "") + '" data-tab="' + entry[0] + '">' + esc(tr(entry[1])) + '</button>';
    }).join("");
  }
  function render() {
    if (!panel) return;
    renderTabs();
    var content = panel.querySelector(".tm-content");
    if (!mod.isReady()) {
      content.innerHTML = '<div class="tm-note">' + esc(tr("请先进入游戏存档或开始新游戏，修改器正在等待游戏运行时就绪。")) + '</div>';
    } else if (tab === "quick") renderQuick(content);
    else if (tab === "bag") renderBag(content);
    else if (tab === "team") renderTeam(content);
    else if (tab === "system") renderSystem(content);
    else if (tab === "map") renderMap(content);
    else if (tab === "battle") renderBattle(content);
    else if (tab === "save") renderSave(content);
    else if (tab === "keys") renderKeys(content);
    else renderAbout(content);
    retryFeatures();
  }
  function retryFeatures() {
    if (!panel || detectTimer || featureAttempts >= 12 ||
        (hasFeature("pokemonParty") && hasFeature("itemBag") && hasFeature("systemData"))) return;
    featureAttempts++;
    detectTimer = setTimeout(function () {
      detectTimer = null;
      var previousFeatures = JSON.stringify(getCaps().features);
      try { var next = mod.listCapabilities(); if (next && next.engine === "essentials") caps = next; }
      catch (error) { console.warn("Essentials capability refresh failed", error); }
      if (previousFeatures !== JSON.stringify(getCaps().features)) render();
      else retryFeatures();
    }, 1800);
  }
  function renderQuick(content) {
    var state = mod.getState() || {};
    var groups = [["队伍与资源", ["hpmp", "status", "items", "coins"]],
      ["探索与对话", ["noEncounter", "noclip", "fastText", "eventSpeed", "msgSkip"]],
      ["战斗", ["invincible", "oneHit", "crit", "escape", "freeSkill"]]];
    var html = groups.map(function (group) {
      var toggles = group[1].filter(hasToggle).map(function (key) {
        return '<label class="tm-pokemon-toggle"><span>' + esc(tr(flagLabels[key] || key)) + '</span>' +
          '<input class="tm-switch" type="checkbox" data-action="flag" data-name="' + key + '" ' + (state[key] ? "checked" : "") + '></label>';
      }).join("");
      return toggles ? card(group[0], toggles) : "";
    }).join("");
    if (hasValue("gold")) {
      var money = 0; try { money = mod.getGold(); } catch (_) {}
      html += card("金钱", '<div class="tm-row"><label class="tm-grow">' + esc(tr("当前金钱")) + ' <strong>' + esc(money) + '</strong></label>' +
        input("tm-pokemon-gold", money, "number", 'min="0" inputmode="numeric"') + button("设置", "set-gold", "primary") + button("拉满", "max-gold") + '</div>');
    }
    if (hasValue("coins")) {
      var coins = 0; try { coins = mod.getCoins(); } catch (_) {}
      html += card("游戏币", '<div class="tm-row"><label class="tm-grow">' + esc(tr("当前游戏币")) + ' <strong>' + esc(coins) + '</strong></label>' +
        input("tm-pokemon-coins", coins, "number", 'min="0" inputmode="numeric"') + button("设置", "set-coins", "primary") + button("拉满", "max-coins") + '</div>');
    }
    if (hasFeature("pokemonRecover")) html += card("队伍恢复", '<div class="tm-row">' + button("恢复全队宝可梦", "recover-party", "primary") + '</div>');
    content.innerHTML = html || '<div class="tm-note">' + esc(tr("当前没有可用的快捷功能")) + '</div>';
  }
  function renderTeam(content) {
    if (!hasFeature("pokemonParty")) { content.innerHTML = '<div class="tm-note">' + esc(tr("宝可梦队伍能力正在初始化")) + '</div>'; return; }
    var party = mod.listPokemon() || [];
    if (!party.length) { content.innerHTML = card("队伍管理", '<div class="tm-note">' + esc(tr("当前队伍里没有宝可梦")) + '</div>'); return; }
    content.innerHTML = '<div class="tm-pokemon-party">' + party.map(function (pokemon) {
      var index = Number(pokemon.index);
      var body = '<div class="tm-pokemon-heading"><strong>' + esc(pokemon.name || ("#" + (index + 1))) + '</strong>' +
        (pokemon.isEgg ? '<span class="tm-badge">' + esc(tr("蛋")) + '</span>' : "") + '</div>' +
        '<div class="tm-pokemon-summary"><span>' + esc(tr("等级")) + ' ' + esc(pokemon.level) + '</span><span>HP ' + esc(pokemon.hp) + '/' + esc(pokemon.mhp) +
        '</span><span>EXP ' + esc(pokemon.exp) + '</span></div>';
      if (pokemon.status) body += '<p class="tm-pokemon-details">' + esc(tr("状态")) + '：' + esc(tr(statusName(pokemon.status))) + '</p>';
      if (pokemon.heldItem) body += '<p class="tm-pokemon-details">' + esc(tr("携带物品")) + '：' + esc(pokemon.heldItem) + '</p>';
      if (pokemon.isEgg) body += '<div class="tm-note">' + esc(tr("蛋不能在这里编辑或恢复")) + '</div>';
      else {
        var valueFields = [];
        if (hasFeature("pokemonLevel")) valueFields.push(valueEditor(index, "level", "等级", pokemon.level));
        if (hasFeature("pokemonHp")) valueFields.push(valueEditor(index, "hp", "当前 HP", pokemon.hp));
        if (hasFeature("pokemonExp")) valueFields.push(valueEditor(index, "exp", "经验", pokemon.exp));
        if (valueFields.length) body += '<div class="tm-pokemon-edit-row">' + valueFields.join("") + '</div>';
        body += '<div class="tm-pokemon-actions">' +
          (hasFeature("pokemonRecover") ? button("恢复", "recover-pokemon", "primary", 'data-index="' + index + '"') : "") +
          (hasFeature("pokemonMoves") ? button("招式", "edit-moves", "", 'data-index="' + index + '"') : "") +
          (hasFeature("pokemonStatus") ? button("状态", "edit-status", "", 'data-index="' + index + '"') : "") +
          (hasFeature("pokemonItem") ? button("携带物品", "edit-item", "", 'data-index="' + index + '"') : "") +
          (hasFeature("pokemonStats") ? button("IV / EV", "edit-stats", "", 'data-index="' + index + '"') : "") + '</div>';
      }
      return '<section class="tm-card tm-pokemon-card">' + body + '</section>';
    }).join("") + '</div>';
  }
  function valueEditor(index, key, label, current) {
    var id = "tm-pokemon-value-" + index + "-" + key;
    return '<label class="tm-pokemon-value"><span>' + esc(tr(label)) + '</span>' + input(id, current, "number", 'min="0" step="1" inputmode="numeric"') +
      button("设置", "set-pokemon-value", "", 'data-index="' + index + '" data-field="' + key + '" data-input="' + id + '"') + '</label>';
  }
  function statusName(status) {
    var names = { SLEEP: "睡眠", POISON: "中毒", BURN: "灼伤", PARALYSIS: "麻痹", FROZEN: "冰冻" };
    return names[String(status || "").toUpperCase()] || status;
  }
  function renderBag(content) {
    if (!hasFeature("itemBag")) { content.innerHTML = '<div class="tm-note">' + esc(tr("背包能力正在初始化")) + '</div>'; return; }
    content.innerHTML = card("背包物品", '<div class="tm-row">' + input("tm-bag-query", itemQuery, "search", 'placeholder="' + esc(tr("搜索背包物品")) + '"') +
      button("搜索", "search-bag", "primary") + '</div><div class="tm-row tm-pokemon-pager"><label><input class="tm-switch" type="checkbox" data-action="show-empty-items" ' +
      (showEmptyItems ? "checked" : "") + '> ' + esc(tr("显示空物品")) + '</label><span class="tm-grow" id="tm-bag-total"></span>' +
      button("上一页", "bag-prev") + button("下一页", "bag-next") + '</div><div id="tm-bag-list" class="tm-list"></div>', "tm-fill-card");
    loadBag();
  }
  function loadBag() {
    var list = panel && panel.querySelector("#tm-bag-list"); if (!list) return;
    var result = mod.listDatabase("item", itemQuery, itemOffset, 100) || { total: 0, values: [] };
    var entries = result.values || [];
    if (!showEmptyItems) entries = entries.filter(function (item) { return Number(item.count) > 0; });
    list.innerHTML = entries.map(function (item) {
      var id = String(item.id); var inputId = "tm-item-count-" + safeId(id);
      return '<div class="tm-item tm-pokemon-bag-row"><label class="tm-pokemon-lock"><input class="tm-switch" type="checkbox" data-action="lock-item" data-id="' + esc(id) + '" ' +
        (item.locked ? "checked" : "") + '></label><span class="tm-name">' + esc(item.name || id) + '</span>' + input(inputId, item.count || 0, "number", 'min="0" max="999999" step="1"') +
        button("设置", "set-item-count", "", 'data-id="' + esc(id) + '" data-input="' + inputId + '"') + '</div>';
    }).join("") || '<div class="tm-note">' + esc(tr(itemQuery ? "没有匹配项目" : "背包里没有物品；搜索名称可添加物品")) + '</div>';
    var total = panel.querySelector("#tm-bag-total"); if (total) total.textContent = tr("共") + " " + String(result.total || 0) + " " + tr("项");
  }
  function renderSystem(content) {
    if (!hasFeature("systemData")) { content.innerHTML = '<div class="tm-note">' + esc(tr("游戏开关/变量能力正在初始化")) + '</div>'; return; }
    content.innerHTML = card("开关与变量", '<div class="tm-row">' +
      '<button class="tm-btn ' + (systemKind === "switch" ? "primary" : "") + '" data-action="system-kind" data-kind="switch">' + esc(tr("开关")) + '</button>' +
      '<button class="tm-btn ' + (systemKind === "variable" ? "primary" : "") + '" data-action="system-kind" data-kind="variable">' + esc(tr("变量")) + '</button>' +
      input("tm-system-query", systemQuery, "search", 'placeholder="' + esc(tr("名称或 ID")) + '"') + button("搜索", "search-system", "primary") + '</div>' +
      '<div class="tm-row tm-pokemon-pager"><label><input class="tm-switch" type="checkbox" data-action="show-unnamed" ' + (showUnnamedSystem ? "checked" : "") +
      '> ' + esc(tr("显示空选项")) + '</label><span class="tm-grow" id="tm-system-total"></span>' + button("上一页", "system-prev") + button("下一页", "system-next") + '</div>' +
      '<div id="tm-system-list" class="tm-list"></div>', "tm-fill-card");
    loadSystem();
  }
  function renderMap(content) {
    var position = {};
    try { position = mod.currentPosition() || {}; } catch (_) {}
    if (!mapId) mapId = Number(position.mapId) || 0;
    var maps = [];
    if (hasFeature("mapControl")) {
      try { maps = (mod.listMaps(mapQuery, 0, 200) || {}).values || []; } catch (_) {}
    }
    var mapRows = maps.map(function (entry) {
      return '<div class="tm-item"><span class="tm-name">#' + esc(entry.id) + ' ' + esc(entry.name || "") +
        (Number(entry.id) === Number(position.mapId) ? ' · ' + esc(tr("当前位置")) : "") + '</span>' + button("选择", "select-map", "", 'data-id="' + esc(entry.id) + '"') + '</div>';
    }).join("") || '<div class="tm-note">' + esc(tr(hasFeature("mapControl") ? "没有匹配地图" : "地图列表对当前游戏不可用")) + '</div>';
    var mapControls = hasFeature("mapControl") ? '<div class="tm-row">' + input("tm-pokemon-map-id", mapId || position.mapId || 0, "number", 'min="1" placeholder="' + esc(tr("地图 ID")) + '"') +
      input("tm-pokemon-map-x", position.x == null ? "" : position.x, "number", 'min="0" placeholder="X"') + input("tm-pokemon-map-y", position.y == null ? "" : position.y, "number", 'min="0" placeholder="Y"') +
      button("传送", "teleport", "primary") + '</div>' + (hasFeature("eventSelfSwitches") ? '<div class="tm-row">' + button("事件开关全开", "map-selfswitches", "", 'data-enabled="1"') + button("事件开关全关", "map-selfswitches", "", 'data-enabled="0"') + '</div>' : "") +
      (hasFeature("minimap") ? '<div class="tm-row">' + button("迷你地图", "show-minimap") + '</div>' : "") : "";
    var eventRows = [];
    if (hasFeature("events")) {
      try { eventRows = (mod.listEvents(mapId || position.mapId || 0) || {}).values || []; } catch (_) {}
    }
    var eventsHtml = !hasFeature("events") ? '<div class="tm-note">' + esc(tr("事件列表对当前游戏不可用")) + '</div>' : !eventRows.length ?
      '<div class="tm-note">' + esc(tr("所选地图没有事件")) + '</div>' : eventRows.map(function (entry) {
      var switches = hasFeature("eventSelfSwitches") ? ["A", "B", "C", "D"].map(function (letter) {
        var enabled = !!(entry.selfSwitches && entry.selfSwitches[letter]);
        return button(letter + (enabled ? "开" : "关"), "event-self-switch", enabled ? "primary" : "", 'data-id="' + esc(entry.id) + '" data-letter="' + letter + '" data-enabled="' + (enabled ? "0" : "1") + ' data-map-id="' + esc(mapId || position.mapId || 0) + '"');
      }).join("") : "";
      return '<div class="tm-item tm-pokemon-event-row"><span class="tm-name">#' + esc(entry.id) + ' ' + esc(entry.name || "") + ' · ' + esc(entry.x) + ',' + esc(entry.y) + '</span>' +
        (hasFeature("mapControl") ? button("传送", "teleport-event", "", 'data-map-id="' + esc(mapId || position.mapId || 0) + '" data-x="' + esc(entry.x) + '" data-y="' + esc(entry.y) + '"') : "") +
        button("详情", "event-details", "", 'data-id="' + esc(entry.id) + '" data-map-id="' + esc(mapId || position.mapId || 0) + '"') +
        '<div class="tm-pokemon-actions">' + switches + '</div></div>';
    }).join("");
    content.innerHTML = '<div class="tm-grid"><div>' +
      (hasFeature("mapControl") ? card("地图操作", mapControls + '<div class="tm-row">' + input("tm-pokemon-map-query", mapQuery, "search", 'placeholder="' + esc(tr("搜索地图 ID / 名称")) + '"') + button("搜索", "search-maps", "primary") + '</div>' + '<div class="tm-list">' + mapRows + '</div>') : "") +
      '</div><div>' + card("地图事件", eventsHtml) + '</div></div>';
  }
  function renderBattle(content) {
    var snapshot = {};
    try { snapshot = mod.battleSnapshot() || {}; } catch (_) {}
    var inBattle = !!snapshot.inBattle;
    var enemies = (snapshot.enemies || []).map(function (enemy) {
      var id = "tm-pokemon-enemy-" + safeId(enemy.index);
      return '<div class="tm-item"><span class="tm-name">' + esc(enemy.name || ("#" + enemy.index)) + ' HP ' + esc(enemy.hp) + '/' + esc(enemy.mhp) + '</span>' +
        input(id, enemy.hp, "number", 'min="0" max="' + esc(enemy.mhp) + '"') + button("设置", "set-enemy-hp", "", 'data-index="' + esc(enemy.index) + '" data-input="' + id + '"') + '</div>';
    }).join("");
    var allies = (snapshot.actors || []).map(function (actor) {
      return '<div class="tm-item"><span class="tm-name">' + esc(actor.name || "") + '</span><span class="tm-badge">HP ' + esc(actor.hp) + '/' + esc(actor.mhp) + '</span></div>';
    }).join("");
    content.innerHTML = '<div class="tm-grid">' + card("战斗状态", inBattle ?
      '<div class="tm-pokemon-actions">' + button("消灭全部敌人", "kill-enemies", "danger") + button("强制胜利", "force-victory", "primary") + button("强制战败", "force-defeat", "danger") + button("强制逃跑", "force-escape") + '</div>' :
      '<div class="tm-note">' + esc(tr("当前不在战斗中")) + '</div>') +
      card("我方宝可梦", '<div class="tm-list">' + (allies || '<div class="tm-note">' + esc(tr("当前不在战斗中")) + '</div>') + '</div>') +
      card("对手宝可梦", '<div class="tm-list">' + (enemies || '<div class="tm-note">' + esc(tr("当前不在战斗中")) + '</div>') + '</div>') + '</div>';
  }
  function renderSave(content) {
    var slots = [];
    try { slots = mod.listSaveSlots() || []; } catch (error) { content.innerHTML = card("存档管理", '<div class="tm-note">' + esc(error.message || String(error)) + '</div>'); return; }
    content.innerHTML = card("存档管理", '<div class="tm-list">' + slots.map(function (slot) {
      var actions = button(slot.exists ? "覆盖" : "保存", "save-slot", "primary", 'data-id="' + esc(slot.id) + '"');
      if (slot.exists) actions += button("读取", "load-slot", "", 'data-id="' + esc(slot.id) + '"') + button("删除", "delete-slot", "danger", 'data-id="' + esc(slot.id) + '"');
      return '<div class="tm-item"><span class="tm-name">' + esc(tr("存档 ")) + esc(slot.id) + ' · ' + esc(slot.exists ? (slot.title || tr("已有数据")) : tr("空")) + '</span>' + actions + '</div>';
    }).join("") + '</div>');
  }
  function inputAvailable() {
    try { return !!(window.TyranorInputNative && window.TyranorInputNative.isAvailable()); } catch (_) { return false; }
  }
  function renderKeys(content) {
    content.innerHTML = card("虚拟按键", '<p>' + esc(tr("按键层由应用原生实现，可自定义按键的位置、文字、大小与输出键位。")) + '</p><div class="tm-row">' +
      button("进入按键编辑", "enter-keys", "primary") + button("恢复默认", "reset-keys") + '</div>');
  }
  function showMinimap() {
    var data;
    try { data = mod.minimapData(); } catch (error) { toast(error.message || String(error), true); return; }
    if (!data || !data.width || !data.height || !data.rows) return toast(tr("无法读取当前地图"), true);
    modal("迷你地图", '<canvas id="tm-pokemon-minimap" class="tm-minimap"></canvas>');
    var canvas = panel.querySelector("#tm-pokemon-minimap");
    var cssWidth = Math.max(120, Math.min(canvas.parentElement.clientWidth || 480, 720));
    var tile = Math.max(2, Math.floor(cssWidth / data.width));
    var cssHeight = Math.max(tile, tile * data.height);
    var dpr = window.devicePixelRatio || 1;
    canvas.style.width = cssWidth + "px"; canvas.style.height = cssHeight + "px";
    canvas.width = Math.round(cssWidth * dpr); canvas.height = Math.round(cssHeight * dpr);
    var context = canvas.getContext("2d"); context.scale(dpr, dpr);
    for (var y = 0; y < data.height; y++) for (var x = 0; x < data.width; x++) {
      context.fillStyle = String(data.rows[y] || "").charAt(x) === "1" ? "#cfe8d6" : "#3a4640";
      context.fillRect(x * tile, y * tile, tile, tile);
    }
    (data.events || []).forEach(function (entry) {
      context.fillStyle = "#ffffff"; context.fillRect(entry.x * tile, entry.y * tile, tile, tile);
    });
    if (data.player) {
      context.strokeStyle = "#22c55e"; context.lineWidth = Math.max(1.5, tile / 4);
      context.strokeRect(data.player.x * tile + 1, data.player.y * tile + 1, Math.max(1, tile - 2), Math.max(1, tile - 2));
    }
  }
  function showEventDetails(eventId, targetMapId) {
    var detail;
    try { detail = mod.eventDetail(Number(eventId), Number(targetMapId)); } catch (error) { toast(error.message || String(error), true); return; }
    if (!detail || detail.error) return toast(tr("无法读取所选事件详情"), true);
    var pages = (detail.pages || []).map(function (page) {
      var conditions = (page.conditions || []).map(function (condition) {
        var typeName = condition.type === "self_switch" ? tr("独立开关") : condition.type === "variable" ? tr("变量") : condition.type === "switch" ? tr("开关") : tr(condition.type || "条件");
        return '<div class="tm-item"><span class="tm-name">' + esc(typeName + " " + condition.id + " · " + (condition.name || "") + " = " + condition.target) + '</span><span class="tm-badge">' + esc(condition.current) + '</span></div>';
      }).join("") || '<div class="tm-note">' + esc(tr("无条件")) + '</div>';
      return '<section class="tm-card"><h3>' + esc(tr("事件页 ") + (Number(page.index) + 1)) + '</h3><p>' + esc(tr("指令数 ") + page.commandCount + " · " + tr("条件数 ") + page.validCount) + '</p>' +
        '<div class="tm-list">' + conditions + '</div><div class="tm-row">' + button("事件解释器", "show-event-interpreter", "", 'data-event-id="' + eventId + '" data-map-id="' + targetMapId + '" data-page="' + page.index + '"') + '</div></section>';
    }).join("") || '<div class="tm-note">' + esc(tr("事件没有可显示的页面")) + '</div>';
    modal("事件详情", '<p><strong>' + esc(detail.name || ("#" + eventId)) + '</strong> · ' + esc(detail.x) + ',' + esc(detail.y) + '</p><div class="tm-pokemon-choice-list">' + pages + '</div>');
  }
  function showEventInterpreter(eventId, targetMapId, pageIndex) {
    var commands;
    try { commands = mod.eventInterpreter(Number(eventId), Number(targetMapId), Number(pageIndex)) || []; }
    catch (error) { toast(error.message || String(error), true); return; }
    var rows = commands.map(function (command) {
      return '<div class="tm-item"><span class="tm-badge">' + esc(command.index) + '</span><span class="tm-name">' + esc(command.preview || ("Code " + command.code)) + '</span></div>';
    }).join("") || '<div class="tm-note">' + esc(tr("事件页没有指令")) + '</div>';
    modal("事件解释器", '<div class="tm-list">' + rows + '</div>');
  }
  function loadSystem() {
    var list = panel && panel.querySelector("#tm-system-list"); if (!list) return;
    var result = systemKind === "switch" ? mod.listSwitches(systemQuery, systemOffset, 100, showUnnamedSystem) :
      mod.listVariables(systemQuery, systemOffset, 100, showUnnamedSystem);
    var entries = result.values || [];
    list.innerHTML = entries.map(function (entry) {
      var id = Number(entry.id); var suffix = id < 0 ? "" : " #" + id; var control;
      if (systemKind === "switch") control = '<input class="tm-switch" type="checkbox" data-action="set-switch" data-id="' + id + '" ' + (entry.value === "true" ? "checked" : "") + '>';
      else if (entry.type === "complex") control = '<span class="tm-badge">' + esc(entry.value) + '</span>';
      else {
        var inputId = "tm-variable-" + safeId(id);
        control = input(inputId, entry.value, entry.type === "number" ? "number" : "text") + button("设置", "set-variable", "", 'data-id="' + id + '" data-input="' + inputId + '"');
      }
      return '<div class="tm-item tm-pokemon-system-row"><label class="tm-pokemon-lock"><input class="tm-switch" type="checkbox" data-action="lock-system" data-id="' + id + '" ' +
        (entry.locked ? "checked" : "") + '></label><span class="tm-name">' + esc(entry.name || ("ID" + suffix)) + esc(suffix) + '</span><span class="tm-pokemon-system-control">' + control + '</span></div>';
    }).join("") || '<div class="tm-note">' + esc(tr("没有匹配项目")) + '</div>';
    var total = panel.querySelector("#tm-system-total"); if (total) total.textContent = tr("共") + " " + String(result.total || 0) + " " + tr("项");
  }
  function renderAbout(content) {
    content.innerHTML = card("宝可梦修改器", '<p>' + esc(tr("宝可梦专属修改面板")) + '</p><p>' +
      esc(tr("队伍、背包和游戏数据只通过当前 Essentials 游戏接口修改。")) + '</p><p>' + esc(tr("重要修改前建议先保存游戏。")) + '</p>');
  }
  function openPokemon() {
    if (panel) return;
    featureAttempts = 0;
    panel = document.createElement("div"); panel.id = "tyranor-mod-root";
    panel.innerHTML = '<div class="tm-panel" role="dialog" aria-modal="true"><div class="tm-head"><strong>' + esc(tr("宝可梦修改器")) +
      '</strong><button class="tm-refresh" data-action="refresh" title="' + esc(tr("刷新面板")) + '" aria-label="' + esc(tr("刷新面板")) + '">↻</button>' +
      '<button class="tm-close" data-action="close" aria-label="' + esc(tr("关闭")) + '">×</button></div><div class="tm-tabs"></div><main class="tm-content"></main></div>';
    document.documentElement.appendChild(panel);
    panel.addEventListener("click", handleClick);
    panel.addEventListener("change", handleChange);
    panel.addEventListener("input", function (event) {
      if (event.target.id === "tm-move-query") renderMoveCandidates();
      if (event.target.id === "tm-held-query") renderHeldCandidates();
    });
    panel.addEventListener("keydown", function (event) {
      if (event.key === "Escape") { event.preventDefault(); closePokemon(true); }
      if (event.key !== "Enter") return;
      if (event.target.id === "tm-bag-query") { event.preventDefault(); itemQuery = value("tm-bag-query").trim(); itemOffset = 0; loadBag(); }
      if (event.target.id === "tm-system-query") { event.preventDefault(); systemQuery = value("tm-system-query").trim(); systemOffset = 0; loadSystem(); }
      if (event.target.id === "tm-pokemon-map-query") { event.preventDefault(); mapQuery = value("tm-pokemon-map-query").trim(); renderMap(panel.querySelector(".tm-content")); }
    });
    panel.addEventListener("click", function (event) { event.stopPropagation(); });
    ["touchstart", "touchmove", "touchend", "pointerdown", "pointerup", "mousedown", "mouseup", "keyup", "wheel"].forEach(function (name) {
      panel.addEventListener(name, function (event) { event.stopPropagation(); });
    });
    render();
  }
  function openProbe() {
    if (probe) return;
    probe = document.createElement("div"); probe.id = "tyranor-mod-root";
    probe.innerHTML = '<div class="tm-panel" role="dialog" aria-modal="true"><div class="tm-head"><strong>' + esc(tr("RGSS 游戏修改器")) +
      '</strong><button class="tm-close" data-action="close" aria-label="' + esc(tr("关闭")) + '">×</button></div><main class="tm-content"><div class="tm-note">' +
      esc(tr("正在识别当前游戏类型…")) + '</div></main></div>';
    document.documentElement.appendChild(probe);
    probe.addEventListener("click", function (event) { if (event.target.closest("[data-action='close']")) close(true); event.stopPropagation(); });
  }
  function detect() {
    detectTimer = null;
    try {
      if (mod.isReady()) {
        var loaded = mod.listCapabilities();
        if (loaded && loaded.engine === "essentials") { caps = loaded; setLauncherLabel("打开宝可梦修改器"); if (probe) { probe.remove(); probe = null; } openPokemon(); return; }
        if (loaded && loaded.engine && loaded.engine !== "unknown") { setLauncherLabel("打开 RPG Maker 修改器"); if (probe) { probe.remove(); probe = null; } rpgUI.open(); return; }
      }
    } catch (error) { console.warn("RGSS cheat panel selection failed", error); }
    detectAttempts++;
    if (detectAttempts < 12) detectTimer = setTimeout(detect, 600);
    else { setLauncherLabel("打开 RPG Maker 修改器"); if (probe) { probe.remove(); probe = null; } rpgUI.open(); }
  }
  function setLauncherLabel(label) {
    var launcher = document.getElementById("tyranor-mod-launcher");
    if (launcher) launcher.setAttribute("aria-label", tr(label));
  }
  function open() {
    if (panel || probe || rpgUI.isOpen()) return;
    detectAttempts = 0; openProbe(); detectTimer = setTimeout(detect, 0);
  }
  function close(restoreFocus) {
    if (detectTimer) clearTimeout(detectTimer); detectTimer = null; detectAttempts = 0;
    if (probe) { probe.remove(); probe = null; notifyClosed(restoreFocus); return true; }
    if (panel) return closePokemon(restoreFocus);
    return rpgUI.close(restoreFocus);
  }
  function closePokemon(restoreFocus) {
    if (!panel) return false;
    if (detectTimer) clearTimeout(detectTimer); detectTimer = null;
    panel.remove(); panel = null;
    var modal = document.querySelector(".tm-pokemon-modal"); if (modal) modal.remove();
    notifyClosed(restoreFocus); return true;
  }
  function notifyClosed(restoreFocus) {
    try { if (window.TyranorCheatNative) window.TyranorCheatNative.onPanelClosed(restoreFocus !== false); } catch (_) {}
  }
  function toggle() { if (panel || probe || rpgUI.isOpen()) close(); else open(); }
  function isOpen() { return !!(panel || probe || rpgUI.isOpen()); }
  function modal(title, body) {
    var old = panel.querySelector(".tm-pokemon-modal"); if (old) old.remove();
    var node = document.createElement("div"); node.className = "tm-modal tm-pokemon-modal";
    node.innerHTML = '<div class="tm-modal-box"><div class="tm-modal-head"><strong>' + esc(tr(title)) + '</strong>' + button("关闭", "close-modal") +
      '</div><div class="tm-modal-body">' + body + '</div></div>';
    node.addEventListener("click", function (event) { if (event.target === node) { node.remove(); event.stopPropagation(); } });
    panel.appendChild(node);
  }
  function askConfirmation(action, id, message) {
    pendingConfirmation = { action: action, id: id };
    modal("确认", '<p>' + esc(tr(message)) + '</p><div class="tm-row">' + button("取消", "close-modal") + button("确认", "confirm-pending", "danger") + '</div>');
  }
  function closeModal() { var node = panel && panel.querySelector(".tm-pokemon-modal"); if (node) node.remove(); }
  function editMoves(index) {
    moveContext = { index: Number(index), slot: 0, candidates: mod.listPokemonMoveCandidates() || [] };
    var moves = mod.listPokemonMoves(index) || [];
    modal("编辑招式", '<div class="tm-pokemon-choice-list">' + moves.map(function (move) {
      return '<div class="tm-item"><span class="tm-name">' + esc(tr("招式位") + " " + (Number(move.slot) + 1) + " · " + (move.name || tr("空"))) + '</span>' +
        button("替换", "choose-move", "", 'data-index="' + index + '" data-slot="' + move.slot + '"') + button("清空", "clear-move", "", 'data-index="' + index + '" data-slot="' + move.slot + '"') + '</div>';
    }).join("") + '</div>');
  }
  function chooseMove(index, slot) {
    moveContext = { index: Number(index), slot: Number(slot), candidates: moveContext ? moveContext.candidates : (mod.listPokemonMoveCandidates() || []) };
    modal("选择替换招式", input("tm-move-query", "", "search", 'placeholder="' + esc(tr("搜索名称或编号")) + '"') + '<div id="tm-move-list" class="tm-list"></div>');
    renderMoveCandidates();
  }
  function renderMoveCandidates() {
    var list = panel && panel.querySelector("#tm-move-list"); if (!list || !moveContext) return;
    var query = String(value("tm-move-query")).trim().toLowerCase();
    var entries = moveContext.candidates.filter(function (entry) {
      return !query || String(entry.name).toLowerCase().indexOf(query) >= 0 || String(entry.key).toLowerCase().indexOf(query) >= 0;
    }).slice(0, 250);
    list.innerHTML = entries.map(function (entry) { return '<button class="tm-item tm-pokemon-choice" data-action="set-move" data-key="' + esc(entry.key) + '">' +
      '<span class="tm-name">' + esc(entry.name || entry.key) + '</span></button>'; }).join("") || '<div class="tm-note">' + esc(tr("没有匹配项目")) + '</div>';
  }
  function editStatus(index) {
    var statuses = mod.listPokemonStatuses(index) || [];
    modal("编辑状态", '<div class="tm-pokemon-choice-list">' + button("清除状态", "set-status", "", 'data-index="' + index + '" data-key=""') +
      statuses.map(function (entry) { return button(statusName(entry.key), "set-status", entry.active ? "primary" : "", 'data-index="' + index + '" data-key="' + esc(entry.key) + '"'); }).join("") + '</div>');
  }
  function editItem(index) {
    selectedPokemon = Number(index);
    modal("设置携带物品", '<div class="tm-row">' + input("tm-held-query", "", "search", 'placeholder="' + esc(tr("搜索名称或编号")) + '"') +
      button("搜索", "search-held", "primary") + '</div><div class="tm-pokemon-choice-list"><button class="tm-item tm-pokemon-choice" data-action="set-held-item" data-key="">' +
      esc(tr("不持有物品")) + '</button><div id="tm-held-list" class="tm-list"></div></div>');
    renderHeldCandidates();
  }
  function renderHeldCandidates() {
    var list = panel && panel.querySelector("#tm-held-list"); if (!list) return;
    var query = String(value("tm-held-query")).trim(); var result = mod.listDatabase("item", query, 0, 100) || { values: [], total: 0 };
    list.innerHTML = (result.values || []).map(function (item) { return '<button class="tm-item tm-pokemon-choice" data-action="set-held-item" data-key="' + esc(item.id) + '">' +
      '<span class="tm-name">' + esc(item.name || item.id) + '</span></button>'; }).join("") || '<div class="tm-note">' + esc(tr("没有匹配项目")) + '</div>';
  }
  function editStats(index) {
    var stats = mod.listPokemonStats(index) || { ivs: [], evs: [] };
    var html = statNames.map(function (name, slot) {
      var ivId = "tm-stat-" + index + "-iv-" + slot; var evId = "tm-stat-" + index + "-ev-" + slot;
      return '<div class="tm-pokemon-stat-row"><strong>' + esc(tr(name)) + '</strong><label>' + esc(tr("IV")) + input(ivId, stats.ivs[slot], "number", 'min="0" max="31" step="1"') +
        button("设置", "set-pokemon-value", "", 'data-index="' + index + '" data-field="iv_' + slot + '" data-input="' + ivId + '"') + '</label><label>' + esc(tr("EV")) +
        input(evId, stats.evs[slot], "number", 'min="0" max="252" step="1"') + button("设置", "set-pokemon-value", "", 'data-index="' + index + '" data-field="ev_' + slot + '" data-input="' + evId + '"') + '</label></div>';
    }).join("");
    modal("编辑 IV / EV", '<div class="tm-pokemon-stats">' + html + '</div>');
  }
  function setPokemonValue(node) {
    var index = Number(node.dataset.index); var key = node.dataset.field || "";
    var raw = String(value(node.dataset.input || ("tm-pokemon-value-" + index + "-" + key))).trim();
    if (!/^\d+$/.test(raw)) { toast(tr("请输入有效的非负整数"), true); return; }
    run(function () { return mod.setPokemonValue(index, key, raw); }, function () {
      toast(tr("数值已修改")); if (key.indexOf("iv_") === 0 || key.indexOf("ev_") === 0) editStats(index); else renderTeam(panel.querySelector(".tm-content"));
    });
  }
  function handleClick(event) {
    var tabNode = event.target.closest("[data-tab]");
    if (tabNode) { tab = tabNode.dataset.tab; render(); event.stopPropagation(); return; }
    var node = event.target.closest("[data-action]"); if (!node) return;
    event.preventDefault(); event.stopPropagation(); var action = node.dataset.action;
    if (action === "close") return close(true);
    if (action === "refresh") { try { caps = mod.listCapabilities(); render(); } catch (error) { toast(error.message || String(error), true); } return; }
    if (action === "close-modal") { pendingConfirmation = null; return closeModal(); }
    if (action === "confirm-pending") {
      var pending = pendingConfirmation; pendingConfirmation = null; closeModal();
      if (!pending) return;
      if (pending.action === "load") return run(function () { return mod.loadGame(Number(pending.id)); }, function () { toast(tr("读取完成")); render(); });
      if (pending.action === "delete") return run(function () { return mod.deleteSave(Number(pending.id)); }, function () { toast(tr("存档已删除")); renderSave(panel.querySelector(".tm-content")); });
      if (pending.action === "reset-keys") {
        try { window.TyranorInputNative.resetToDefaults(); toast(tr("虚拟按键已恢复默认")); } catch (error) { toast(error.message || String(error), true); }
      }
      return;
    }
    if (action === "flag") return;
    if (action === "set-gold" || action === "max-gold") {
      var amount = action === "max-gold" ? 99999999 : Number(value("tm-pokemon-gold"));
      if (!Number.isFinite(amount) || amount < 0) return toast(tr("请输入有效金额"), true);
      return run(function () { return mod.setGold(amount); }, function () { toast(tr("金钱已修改")); renderQuick(panel.querySelector(".tm-content")); });
    }
    if (action === "set-coins" || action === "max-coins") {
      var coinAmount = action === "max-coins" ? 999999 : Number(value("tm-pokemon-coins"));
      if (!Number.isFinite(coinAmount) || coinAmount < 0) return toast(tr("请输入有效金额"), true);
      return run(function () { return mod.setCoins(coinAmount); }, function () { toast(tr("游戏币已修改")); renderQuick(panel.querySelector(".tm-content")); });
    }
    if (action === "recover-pokemon") return run(function () { return mod.recoverPokemon(Number(node.dataset.index)); }, function () { toast(tr("宝可梦已恢复")); renderTeam(panel.querySelector(".tm-content")); });
    if (action === "recover-party") return run(function () { return mod.recoverParty(); }, function () { toast(tr("宝可梦队伍已恢复")); if (tab === "team") renderTeam(panel.querySelector(".tm-content")); });
    if (action === "set-pokemon-value") return setPokemonValue(node);
    if (action === "edit-moves") return editMoves(node.dataset.index);
    if (action === "choose-move") return chooseMove(node.dataset.index, node.dataset.slot);
    if (action === "clear-move") return run(function () { return mod.setPokemonMove(Number(node.dataset.index), Number(node.dataset.slot), ""); }, function () { closeModal(); toast(tr("招式已更新")); renderTeam(panel.querySelector(".tm-content")); });
    if (action === "set-move" && moveContext) return run(function () { return mod.setPokemonMove(moveContext.index, moveContext.slot, node.dataset.key || ""); }, function () { closeModal(); toast(tr("招式已更新")); renderTeam(panel.querySelector(".tm-content")); });
    if (action === "edit-status") return editStatus(node.dataset.index);
    if (action === "set-status") return run(function () { return mod.setPokemonStatus(Number(node.dataset.index), node.dataset.key || ""); }, function () { closeModal(); toast(tr("状态已更新")); renderTeam(panel.querySelector(".tm-content")); });
    if (action === "edit-item") return editItem(node.dataset.index);
    if (action === "search-held") return renderHeldCandidates();
    if (action === "set-held-item") return run(function () { return mod.setPokemonItem(selectedPokemon, node.dataset.key || ""); }, function () { closeModal(); toast(tr("携带物品已更新")); renderTeam(panel.querySelector(".tm-content")); });
    if (action === "edit-stats") return editStats(node.dataset.index);
    if (action === "search-bag") { itemQuery = value("tm-bag-query").trim(); itemOffset = 0; return loadBag(); }
    if (action === "bag-prev") { itemOffset = Math.max(0, itemOffset - 100); return loadBag(); }
    if (action === "bag-next") { itemOffset += 100; return loadBag(); }
    if (action === "set-item-count") {
      var quantity = Number(value(node.dataset.input)); if (!Number.isInteger(quantity) || quantity < 0 || quantity > 999999) return toast(tr("请输入 0 到 999999 的整数"), true);
      return run(function () { return mod.setItemCount("item", node.dataset.id, quantity); }, function () { toast(tr("数量已修改")); loadBag(); });
    }
    if (action === "search-system") { systemQuery = value("tm-system-query").trim(); systemOffset = 0; return loadSystem(); }
    if (action === "system-kind") { systemKind = node.dataset.kind; systemOffset = 0; return render(); }
    if (action === "system-prev") { systemOffset = Math.max(0, systemOffset - 100); return loadSystem(); }
    if (action === "system-next") { systemOffset += 100; return loadSystem(); }
    if (action === "set-variable") return run(function () { return mod.setVariable(Number(node.dataset.id), value(node.dataset.input)); }, function () { toast(tr("变量已修改")); loadSystem(); });
    if (action === "search-maps") { mapQuery = value("tm-pokemon-map-query").trim(); return renderMap(panel.querySelector(".tm-content")); }
    if (action === "select-map") { mapId = Number(node.dataset.id) || 0; return renderMap(panel.querySelector(".tm-content")); }
    if (action === "teleport" || action === "teleport-event") {
      var destinationMap = Number(action === "teleport-event" ? node.dataset.mapId : value("tm-pokemon-map-id"));
      var destinationX = Number(action === "teleport-event" ? node.dataset.x : value("tm-pokemon-map-x"));
      var destinationY = Number(action === "teleport-event" ? node.dataset.y : value("tm-pokemon-map-y"));
      if (!Number.isInteger(destinationMap) || destinationMap < 1 || !Number.isInteger(destinationX) || destinationX < 0 || !Number.isInteger(destinationY) || destinationY < 0) return toast(tr("请输入有效的地图 ID 和坐标"), true);
      return run(function () { return mod.teleport(destinationMap, destinationX, destinationY, 2, false); }, function () { mapId = destinationMap; toast(tr("传送完成")); renderMap(panel.querySelector(".tm-content")); });
    }
    if (action === "event-self-switch") return run(function () {
      return mod.setEventSelfSwitch(Number(node.dataset.id), node.dataset.letter, node.dataset.enabled === "1", Number(node.dataset.mapId));
    }, function () { toast(tr("独立开关已更新")); renderMap(panel.querySelector(".tm-content")); });
    if (action === "map-selfswitches") return run(function () {
      return mod.setMapEventSelfSwitches(mapId || Number(value("tm-pokemon-map-id")), node.dataset.enabled === "1");
    }, function () { toast(tr("事件开关已更新")); renderMap(panel.querySelector(".tm-content")); });
    if (action === "show-minimap") return showMinimap();
    if (action === "event-details") return showEventDetails(node.dataset.id, node.dataset.mapId);
    if (action === "show-event-interpreter") return showEventInterpreter(node.dataset.eventId, node.dataset.mapId, node.dataset.page);
    if (action === "set-enemy-hp") {
      var enemyHp = Number(value(node.dataset.input));
      if (!Number.isInteger(enemyHp) || enemyHp < 0) return toast(tr("请输入有效的非负整数"), true);
      return run(function () { return mod.setEnemyHp(Number(node.dataset.index), enemyHp); }, function () { toast(tr("宝可梦 HP 已修改")); renderBattle(panel.querySelector(".tm-content")); });
    }
    if (action === "kill-enemies" || action === "force-victory" || action === "force-defeat" || action === "force-escape") {
      var battleAction = action === "kill-enemies" ? "killAllEnemies" : action === "force-victory" ? "forceVictory" : action === "force-defeat" ? "forceDefeat" : "forceEscape";
      return run(function () { return mod[battleAction](); }, function () { toast(tr("战斗操作已执行")); renderBattle(panel.querySelector(".tm-content")); });
    }
    if (action === "save-slot") return run(function () { return mod.saveGame(Number(node.dataset.id)); }, function () { toast(tr("保存完成")); renderSave(panel.querySelector(".tm-content")); });
    if (action === "load-slot") {
      return askConfirmation("load", node.dataset.id, "确定读取这个存档吗？当前未保存的进度会丢失。");
    }
    if (action === "delete-slot") {
      return askConfirmation("delete", node.dataset.id, "确定删除这个存档吗？此操作无法撤销。");
    }
    if (action === "enter-keys") {
      try { window.TyranorInputNative.enterEdit(); } catch (error) { toast(error.message || String(error), true); }
      return;
    }
    if (action === "reset-keys") {
      return askConfirmation("reset-keys", 0, "确定恢复虚拟按键默认布局吗？");
    }
  }
  function handleChange(event) {
    var node = event.target; var action = node.dataset.action;
    if (action === "flag") return run(function () { return mod.setFlag(node.dataset.name, node.checked); }, function () { toast(tr("设置已更新")); });
    if (action === "show-empty-items") { showEmptyItems = node.checked; return loadBag(); }
    if (action === "show-unnamed") { showUnnamedSystem = node.checked; systemOffset = 0; return loadSystem(); }
    if (action === "lock-item") return run(function () { return mod.setItemLock("item", node.dataset.id, node.checked); }, function () { toast(tr("物品已锁定")); });
    if (action === "set-switch") return run(function () { return mod.setSwitch(Number(node.dataset.id), node.checked); }, loadSystem);
    if (action === "lock-system") return run(function () { return mod.setSystemLock(systemKind, Number(node.dataset.id), node.checked); }, loadSystem);
  }
  function open() {
    if (panel || probe || rpgUI.isOpen()) return;
    detectAttempts = 0; openProbe(); detect();
  }
  function openProbe() {
    probe = document.createElement("div"); probe.id = "tyranor-mod-root";
    probe.innerHTML = '<div class="tm-panel" role="dialog" aria-modal="true"><div class="tm-head"><strong>' + esc(tr("RGSS 游戏修改器")) +
      '</strong><button class="tm-close" data-action="close" aria-label="' + esc(tr("关闭")) + '">×</button></div><main class="tm-content"><div class="tm-note">' + esc(tr("正在识别当前游戏类型…")) + '</div></main></div>';
    document.documentElement.appendChild(probe);
    probe.addEventListener("click", function (event) { if (event.target.closest("[data-action='close']")) close(true); event.stopPropagation(); });
  }
  function detect() {
    detectTimer = null;
    try {
      if (mod.isReady()) {
        var loaded = mod.listCapabilities();
        if (loaded && loaded.engine === "essentials") { caps = loaded; probe.remove(); probe = null; openPokemon(); return; }
        if (loaded && loaded.engine && loaded.engine !== "unknown") { probe.remove(); probe = null; rpgUI.open(); return; }
      }
    } catch (error) { console.warn("RGSS cheat panel selection failed", error); }
    detectAttempts++;
    if (detectAttempts < 12) detectTimer = setTimeout(detect, 600);
    else { if (probe) { probe.remove(); probe = null; } rpgUI.open(); }
  }
  function close(restoreFocus) {
    if (detectTimer) clearTimeout(detectTimer); detectTimer = null; detectAttempts = 0;
    if (probe) { probe.remove(); probe = null; notifyClosed(restoreFocus); return true; }
    if (panel) {
      panel.remove(); panel = null;
      var modalNode = document.querySelector(".tm-pokemon-modal"); if (modalNode) modalNode.remove();
      notifyClosed(restoreFocus); return true;
    }
    return rpgUI.close(restoreFocus);
  }
  function notifyClosed(restoreFocus) {
    try { if (window.TyranorCheatNative) window.TyranorCheatNative.onPanelClosed(restoreFocus !== false); } catch (_) {}
  }
  function toggle() { if (panel || probe || rpgUI.isOpen()) close(); else open(); }
  function isOpen() { return !!(panel || probe || rpgUI.isOpen()); }
  window.TyranorModUI = { open: open, close: close, toggle: toggle, isOpen: isOpen };
})();
