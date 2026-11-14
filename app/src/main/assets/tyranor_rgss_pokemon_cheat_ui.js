(function () {
  "use strict";
  var mod = window.TyranorMod;
  var rpgUI = window.TyranorModUI;
  if (!mod || !rpgUI) return;

  var panel = null;
  var probe = null;
  var caps = null;
  var tab = "team";
  var teamView = "party";
  var teamSection = "stats";
  var teamQuery = "";
  var teamPokemon = [];
  var quickSection = "resources";
  var storageBox = -1;
  var pendingDepositIndex = null;
  var detectTimer = null;
  var engineDetectionTimer = null;
  var detectedEngine = mod.launchEngine || "unknown";
  var engineDetectionAttempts = 0;
  var MAX_ENGINE_DETECTION_ATTEMPTS = 24;
  var featureAttempts = 0;
  var itemQuery = "";
  var itemOffset = 0;
  var bagCategory = "";
  var bagCategories = [];
  var selectedItemId = null;
  var showEmptyItems = true;
  var systemKind = "switch";
  var systemQuery = "";
  var systemKindExpanded = false;
  var hideEmptySystemEntries = false;
  var mapId = 0;
  var currentMapId = 0;
  var mapListExpanded = false;
  var mapEntries = [];
  var eventId = 0;
  var eventEntries = [];
  var selectedEventDetail = null;
  var minimapSelection = null;
  var selectedPokemon = 0;
  var moveContext = null;
  var pendingConfirmation = null;
  var addPokemonQuery = "";
  var statNames = ["HP", "攻击", "防御", "特攻", "特防", "速度"];
  var flagLabels = {
    hpmp: "HP/PP不减", gold: "金钱不减", status: "异常状态免疫", items: "背包物品不减", coins: "游戏币不减",
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
  function card(title, body, cls, headerControl) {
    var heading = headerControl
      ? '<div class="tm-card-heading"><h3>' + esc(tr(title)) + '</h3>' + headerControl + '</div>'
      : '<h3>' + esc(tr(title)) + '</h3>';
    return '<section class="tm-card' + (cls ? " " + cls : "") + '">' + heading + body + '</section>';
  }
  function segmentedTabs(items, selected, action, dataName) {
    dataName = dataName || "section";
    return '<div class="tm-segmented-tabs">' + items.map(function (item) {
      return '<button type="button" class="tm-segmented-tab' + (item.id === selected ? ' active' : '') + '" data-action="' + action + '" data-' + dataName + '="' + esc(item.id) + '">' + esc(tr(item.label)) + '</button>';
    }).join("") + '</div>';
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
  function systemSwitchOn(value) { return value === true || value === 1 || String(value).toLowerCase() === "true" || String(value) === "1"; }
  function isDashPlaceholder(value) {
    var name = String(value == null ? "" : value).replace(/\s+/g, "");
    return !!name && /^[\-‐‑‒–—―−﹣－_＿]+$/.test(name);
  }
  function visibleTabs() {
    var tabs = [["quick", "快捷操作"]];
    if (hasFeature("itemBag")) tabs.push(["bag", "背包"]);
    if (hasFeature("pokemonParty")) tabs.push(["team", "队伍"]);
    if (hasFeature("pokemonBadges") || hasFeature("pokemonDex")) tabs.push(["progress", "进度"]);
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
    else if (tab === "progress") renderProgress(content);
    else if (tab === "system") renderSystem(content);
    else if (tab === "map") renderMap(content);
    else if (tab === "battle") renderBattle(content);
    else if (tab === "save") renderSave(content);
    else if (tab === "keys") renderKeys(content);
    else renderAbout(content);
    retryFeatures();
  }
  function retryFeatures() {
    if (!panel || detectTimer ||
        (hasFeature("pokemonPartyReady") && hasFeature("itemBagReady"))) return;
    featureAttempts = Math.min(featureAttempts + 1, 12);
    detectTimer = setTimeout(function () {
      detectTimer = null;
      var previousFeatures = JSON.stringify(getCaps().features);
      try { var next = mod.listCapabilities(); if (next && next.engine === "essentials") caps = next; }
      catch (error) { console.warn("Essentials capability refresh failed", error); }
      if (previousFeatures !== JSON.stringify(getCaps().features)) render();
      else retryFeatures();
    }, featureAttempts < 12 ? 1800 : 5000);
  }
  function renderQuick(content) {
    var state = mod.getState() || {};
    var groups = [
      { id: "resources", label: "队伍与资源", toggles: ["hpmp", "gold", "status", "items", "coins"], currencies: true },
      { id: "explore", label: "探索与对话", toggles: ["noEncounter", "noclip", "fastText", "eventSpeed", "msgSkip"], speed: true },
      { id: "battle", label: "战斗", toggles: ["invincible", "oneHit", "crit", "escape", "freeSkill"] }
    ].map(function (group) {
      group.toggles = group.toggles.filter(hasToggle);
      group.available = group.toggles.length > 0 || (group.currencies && (hasValue("gold") || hasValue("coins") ||
        (hasFeature("pokemonRecover") && hasFeature("pokemonPartyReady")))) ||
        (group.speed && (hasValue("speed") || hasFeature("pokemonGameSpeed")));
      return group;
    }).filter(function (group) { return group.available; });
    if (!groups.length) { content.innerHTML = '<div class="tm-note">' + esc(tr("当前没有可用的快捷功能")) + '</div>'; return; }
    if (!groups.some(function (group) { return group.id === quickSection; })) quickSection = groups[0].id;
    var selected = groups.filter(function (group) { return group.id === quickSection; })[0];
    var body = selected.toggles.map(function (key) {
        return '<label class="tm-pokemon-toggle"><span>' + esc(tr(flagLabels[key] || key)) + '</span>' +
          '<input class="tm-switch" type="checkbox" data-action="flag" data-name="' + key + '" ' + (state[key] ? "checked" : "") + '></label>';
      }).join("");
    if (selected.currencies) {
      if (hasValue("gold")) {
        var money = 0; try { money = mod.getGold(); } catch (_) {}
        body += '<div class="tm-row tm-gold-row"><label>' + esc(tr("金钱")) + '：<strong>' + esc(money) + '</strong></label>' +
          input("tm-pokemon-gold", money, "number", 'min="0" inputmode="numeric"') + button("设置", "set-gold", "primary") + button("拉满", "max-gold") + '</div>';
      }
      if (hasValue("coins")) {
        var coins = 0; try { coins = mod.getCoins(); } catch (_) {}
        body += '<div class="tm-row"><label class="tm-grow">' + esc(tr("游戏币")) + '：<strong>' + esc(coins) + '</strong></label>' +
          input("tm-pokemon-coins", coins, "number", 'min="0" inputmode="numeric"') + button("设置", "set-coins", "primary") + button("拉满", "max-coins") + '</div>';
      }
      if (hasFeature("pokemonRecover") && hasFeature("pokemonPartyReady")) body += '<div class="tm-row">' + button("恢复全队宝可梦", "recover-party", "primary") + '</div>';
    }
    if (selected.speed) {
      if (hasValue("speed")) body += '<div class="tm-row"><label class="tm-grow">' + esc(tr("移动速度")) + '</label>' +
        input("tm-pokemon-speed", state.speedMultiplier || 2, "number", 'min="0.25" max="8" step="0.25"') + button("设置", "set-speed", "primary") + '</div>';
      if (hasFeature("pokemonGameSpeed")) body += '<div class="tm-row"><label class="tm-grow">' + esc(tr("游戏倍速")) + '</label>' +
        input("tm-pokemon-game-speed", state.gameSpeedMultiplier || 1, "number", 'min="1" max="9" step="1"') + button("设置", "set-game-speed", "primary") + '</div>';
    }
    content.innerHTML = segmentedTabs(groups, quickSection, "quick-section") + card(selected.label, body || '<div class="tm-note">' + esc(tr("当前没有可用的快捷功能")) + '</div>');
  }
  function renderTeam(content) {
    if (!hasFeature("pokemonParty")) { content.innerHTML = '<div class="tm-note">' + esc(tr("宝可梦队伍能力正在初始化")) + '</div>'; return; }
    if (!hasFeature("pokemonPartyReady")) { content.innerHTML = '<div class="tm-note">' + esc(tr("开始新游戏或载入存档后即可读取宝可梦队伍")) + '</div>'; return; }
    if (teamView === "storage" && hasFeature("pokemonStorage")) return renderPokemonStorage(content);
    teamPokemon = mod.listPokemon() || [];
    var views = [{ id: "party", label: "队伍" }];
    if (hasFeature("pokemonStorage")) views.push({ id: "storage", label: "宝可梦仓库" });
    var search = input("tm-team-query", teamQuery, "search", 'placeholder="' + esc(tr("搜索宝可梦名称或编号")) + '"');
    var actions = hasFeature("pokemonObtain") || hasFeature("pokemonRecover") ? '<div class="tm-row">' +
      (hasFeature("pokemonObtain") ? button("获得宝可梦", "open-add-pokemon", "primary") : "") +
      (hasFeature("pokemonRecover") ? button("恢复全队", "recover-party") : "") + '</div>' : "";
    content.innerHTML = segmentedTabs(views, teamView, "team-view", "view") +
      '<div class="tm-grid tm-actor-layout tm-pokemon-team-layout">' +
      card("队伍管理", search + actions + '<div id="tm-pokemon-roster" class="tm-list tm-actor-list"></div>', "tm-actor-roster") +
      '<section class="tm-card tm-actor-detail" id="tm-pokemon-detail"></section></div>';
    renderTeamRoster();
  }
  function renderTeamRoster() {
    var roster = panel && panel.querySelector("#tm-pokemon-roster");
    if (!roster) return;
    var entries = teamPokemon.filter(function (pokemon) {
      return !teamQuery || String(pokemon.name || "").toLowerCase().indexOf(teamQuery.toLowerCase()) >= 0 ||
        String(pokemon.index).indexOf(teamQuery) >= 0;
    });
    if (!entries.some(function (pokemon) { return Number(pokemon.index) === Number(selectedPokemon); })) {
      selectedPokemon = entries.length ? Number(entries[0].index) : null;
    }
    var rosterRows = entries.map(function (pokemon) {
      var index = Number(pokemon.index);
      var selected = index === Number(selectedPokemon);
      return '<div class="tm-item tm-actor-row' + (selected ? ' selected' : '') + '"><button type="button" class="tm-actor-select" data-action="select-pokemon" data-index="' + index + '">' +
        '<span class="tm-name">#' + (index + 1) + ' ' + esc(pokemon.name || tr("未命名宝可梦")) + '</span></button>' +
        '<span class="tm-badge">' + (pokemon.isEgg ? esc(tr("蛋")) : esc(tr("等级")) + " " + esc(pokemon.level)) + '</span></div>';
    }).join("");
    if (!rosterRows) rosterRows = '<div class="tm-note">' + esc(tr(teamPokemon.length ? "没有匹配宝可梦" : "当前队伍里没有宝可梦")) + '</div>';
    roster.innerHTML = rosterRows;
    var pokemon = entries.filter(function (entry) { return Number(entry.index) === Number(selectedPokemon); })[0] || null;
    renderPokemonDetail(pokemon, teamPokemon.length);
  }
  function renderPokemonDetail(pokemon, partyLength) {
    var detail = panel && panel.querySelector("#tm-pokemon-detail");
    if (!detail) return;
    if (!pokemon) {
      detail.innerHTML = '<div class="tm-note">' + esc(tr(teamPokemon.length ? "请选择队伍中的宝可梦" : "当前队伍里没有宝可梦")) + '</div>';
      return;
    }
    var index = Number(pokemon.index);
    var sections = [];
    if (pokemon.isEgg ? hasFeature("pokemonEggSteps") :
      (hasFeature("pokemonLevel") || hasFeature("pokemonHp") || hasFeature("pokemonExp") || hasFeature("pokemonHappiness"))) {
      sections.push({ id: "stats", label: pokemon.isEgg ? "孵化" : "属性" });
    }
    if (!pokemon.isEgg && hasFeature("pokemonMoves")) sections.push({ id: "moves", label: "招式" });
    if (!pokemon.isEgg && hasFeature("pokemonStatus")) sections.push({ id: "status", label: "状态" });
    if (!pokemon.isEgg && hasFeature("pokemonItem")) sections.push({ id: "item", label: "携带物" });
    if (!pokemon.isEgg && hasFeature("pokemonStats")) sections.push({ id: "iv_ev", label: "IV / EV" });
    if (!pokemon.isEgg && hasFeature("pokemonTraits")) sections.push({ id: "traits", label: "个体属性" });
    if (!sections.some(function (entry) { return entry.id === teamSection; })) teamSection = sections.length ? sections[0].id : "";
    var heading = '<div class="tm-actor-detail-heading"><h3>' + esc(pokemon.name || tr("未命名宝可梦")) + '</h3>' +
      '<span class="tm-badge">' + (pokemon.isEgg ? esc(tr("蛋")) : esc(tr("等级")) + " " + esc(pokemon.level)) + '</span>' +
      (hasFeature("pokemonRecover") && !pokemon.isEgg ? button("恢复", "recover-pokemon", "primary", 'data-index="' + index + '"') : "") + '</div>' +
      '<div class="tm-pokemon-summary"><span>HP ' + esc(pokemon.hp) + '/' + esc(pokemon.mhp) + '</span><span>' + esc(tr("经验")) + ' ' + esc(pokemon.exp) + '</span>' +
      (pokemon.status ? '<span>' + esc(tr(statusName(pokemon.status))) + '</span>' : "") + (pokemon.heldItem ? '<span>' + esc(pokemon.heldItem) + '</span>' : "") + '</div>';
    var management = '<div class="tm-pokemon-actions">' +
      (hasFeature("pokemonNickname") ? button("修改昵称", "edit-nickname", "", 'data-index="' + index + '" data-name="' + esc(pokemon.nickname || "") + '"') : "") +
      (hasFeature("pokemonStorage") ? button("存入仓库", "deposit-pokemon", "", 'data-index="' + index + '"') : "") +
      (hasFeature("pokemonStorage") ? button("释放", "confirm-release-pokemon", "danger", 'data-box="-1" data-slot="' + index + '" data-name="' + esc(pokemon.name) + '"') : "") +
      (hasFeature("pokemonPartyOrder") && index > 0 ? button("上移", "reorder-pokemon", "", 'data-index="' + index + '" data-target="' + (index - 1) + '"') : "") +
      (hasFeature("pokemonPartyOrder") && index < partyLength - 1 ? button("下移", "reorder-pokemon", "", 'data-index="' + index + '" data-target="' + (index + 1) + '"') : "") + '</div>';
    detail.innerHTML = heading + management + (sections.length ? segmentedTabs(sections, teamSection, "pokemon-section") + '<div id="tm-actor-section"></div>' : '');
    if (sections.length) renderPokemonSection(pokemon);
  }
  function renderPokemonSection(pokemon) {
    var section = panel && panel.querySelector("#tm-actor-section");
    if (!section || !pokemon) return;
    var index = Number(pokemon.index);
    if (teamSection === "stats") {
      var fields = [];
      if (pokemon.isEgg) {
        if (hasFeature("pokemonEggSteps")) fields.push(valueEditor(index, "eggSteps", "剩余孵化步数", pokemon.eggSteps));
        section.innerHTML = card("孵化进度", '<div class="tm-list">' + fields.join("") + '</div>' +
          '<div class="tm-row">' + button("快速孵化", "hatch-egg", "primary", 'data-index="' + index + '"') + '</div>');
        return;
      }
      if (hasFeature("pokemonLevel")) fields.push(valueEditor(index, "level", "等级", pokemon.level));
      if (hasFeature("pokemonHp")) fields.push(valueEditor(index, "hp", "当前 HP", pokemon.hp));
      if (hasFeature("pokemonExp")) fields.push(valueEditor(index, "exp", "经验", pokemon.exp));
      if (hasFeature("pokemonHappiness")) fields.push(valueEditor(index, "happiness", "亲密度", pokemon.happiness));
      section.innerHTML = card("属性", fields.length ? '<div class="tm-list">' + fields.join("") + '</div>' :
        '<div class="tm-note">' + esc(tr("当前游戏没有可修改的宝可梦属性接口")) + '</div>');
    } else if (teamSection === "moves") {
      var moves = mod.listPokemonMoves(index) || [];
      var moveRows = moves.map(function (move) {
        return '<div class="tm-item"><span class="tm-name">' + esc(tr("招式位") + " " + (Number(move.slot) + 1) + " · " + (move.name || tr("空"))) + '</span>' +
          (move.pp == null ? "" : '<span class="tm-badge">PP ' + esc(move.pp) + (move.totalPp == null ? "" : "/" + esc(move.totalPp)) + '</span>') + '</div>';
      }).join("");
      section.innerHTML = card("招式", '<div class="tm-list">' + (moveRows || '<div class="tm-note">' + esc(tr("没有已学招式")) + '</div>') + '</div>' +
        '<div class="tm-row">' + button("编辑招式与 PP", "edit-moves", "primary", 'data-index="' + index + '"') + '</div>');
    } else if (teamSection === "status") {
      var statuses = mod.listPokemonStatuses(index) || [];
      var statusRows = statuses.map(function (entry) {
        return '<div class="tm-item"><span class="tm-name">' + esc(tr(statusName(entry.key))) + '</span>' +
          button(entry.active ? "清除" : "设置", "set-status", entry.active ? "" : "primary", 'data-index="' + index + '" data-key="' + (entry.active ? "" : esc(entry.key)) + '"') + '</div>';
      }).join("");
      section.innerHTML = card("状态", '<div class="tm-list">' + (statusRows || '<div class="tm-note">' + esc(tr("当前游戏没有可编辑的异常状态")) + '</div>') + '</div>');
    } else if (teamSection === "item") {
      section.innerHTML = card("携带物品", '<div class="tm-item"><span class="tm-name">' + esc(pokemon.heldItem || tr("未携带物品")) + '</span></div>' +
        '<div class="tm-row">' + button("选择携带物品", "edit-item", "primary", 'data-index="' + index + '"') + '</div>');
    } else if (teamSection === "iv_ev") {
      var stats = mod.listPokemonStats(index) || { ivs: [], evs: [] };
      var statRows = statNames.map(function (name, slot) {
        var ivId = "tm-stat-" + index + "-iv-" + slot; var evId = "tm-stat-" + index + "-ev-" + slot;
        return '<div class="tm-pokemon-stat-row"><strong>' + esc(tr(name)) + '</strong><label>' + esc(tr("IV")) + input(ivId, stats.ivs[slot], "number", 'min="0" max="31" step="1"') +
          button("设置", "set-pokemon-value", "", 'data-index="' + index + '" data-field="iv_' + slot + '" data-input="' + ivId + '"') + '</label><label>' + esc(tr("EV")) +
          input(evId, stats.evs[slot], "number", 'min="0" max="252" step="1"') + button("设置", "set-pokemon-value", "", 'data-index="' + index + '" data-field="ev_' + slot + '" data-input="' + evId + '"') + '</label></div>';
      }).join("");
      section.innerHTML = card("个体值与努力值", '<div class="tm-pokemon-stats">' + statRows + '</div>');
    } else if (teamSection === "traits") {
      var traits = mod.listPokemonTraits(index) || [];
      var traitLabels = { nature: "性格", ability: "特性", form: "形态", gender: "性别", shiny: "闪光", superShiny: "超闪光" };
      var traitRows = traits.map(function (trait) {
        return '<div class="tm-pokemon-trait"><strong>' + esc(tr(traitLabels[trait.field] || trait.field)) + '</strong><span class="tm-pokemon-details">' +
          esc(tr("当前值")) + '：' + esc(trait.currentName) + '</span><div class="tm-pokemon-choice-list">' + (trait.options || []).map(function (option) {
            return button(option.name, "set-pokemon-trait", String(option.key) === String(trait.currentKey) ? "primary" : "", 'data-index="' + index + '" data-field="' + esc(trait.field) + '" data-key="' + esc(option.key) + '"');
          }).join("") + '</div></div>';
      }).join("");
      section.innerHTML = card("个体属性", traitRows || '<div class="tm-note">' + esc(tr("当前宝可梦没有可修改的个体属性")) + '</div>');
    }
  }
  function renderPokemonStorage(content) {
    var box = mod.listPokemonStorage(storageBox);
    if (!box) { content.innerHTML = '<div class="tm-note">' + esc(tr("无法读取宝可梦仓库")) + '</div>'; return; }
    storageBox = Number(box.boxIndex) || 0;
    var views = segmentedTabs([{ id: "party", label: "队伍" }, { id: "storage", label: "宝可梦仓库" }], "storage", "team-view", "view");
    var navigation = '<div class="tm-row tm-pokemon-box-nav">' + button("上一盒", "storage-prev", "", 'data-box="' + storageBox + '"') +
      '<strong class="tm-grow">' + esc(box.name) + ' · ' + esc(storageBox + 1) + '/' + esc(box.boxCount) + '</strong>' +
      button("下一盒", "storage-next", "", 'data-box="' + storageBox + '"') + '</div>';
    var deposit = pendingDepositIndex == null ? '<div class="tm-note">' + esc(tr("从队伍选择宝可梦即可存入当前仓库盒")) + '</div>' :
      '<div class="tm-row"><span class="tm-grow">' + esc(tr("已选择队伍中的宝可梦")) + '</span>' +
      button("存入当前盒子", "store-selected-pokemon", "primary", 'data-index="' + pendingDepositIndex + '" data-box="' + storageBox + '"') +
      button("取消", "cancel-deposit-pokemon") + '</div>';
    var rows = (box.pokemon || []).map(function (pokemon) {
      var slot = Number(pokemon.index);
      return '<div class="tm-item tm-pokemon-box-row"><span class="tm-name">' + esc(pokemon.name) + '</span>' +
        '<span class="tm-badge">' + esc(tr("等级")) + ' ' + esc(pokemon.level) + '</span>' +
        '<span class="tm-badge">HP ' + esc(pokemon.hp) + '/' + esc(pokemon.mhp) + '</span>' +
        (pokemon.heldItem ? '<span class="tm-badge">' + esc(pokemon.heldItem) + '</span>' : "") +
        (pokemon.isEgg ? '<span class="tm-badge">' + esc(tr("蛋")) + '</span>' : "") +
        '<div class="tm-pokemon-actions">' + button("取出到队伍", "withdraw-pokemon", "primary", 'data-box="' + storageBox + '" data-slot="' + slot + '"') +
        button("释放", "confirm-release-pokemon", "danger", 'data-box="' + storageBox + '" data-slot="' + slot + '" data-name="' + esc(pokemon.name) + '"') + '</div></div>';
    }).join("") || '<div class="tm-note">' + esc(tr("这个盒子是空的")) + '</div>';
    content.innerHTML = views + card("宝可梦仓库", navigation + deposit + '<div class="tm-list">' + rows + '</div>');
  }
  function renderProgress(content) {
    var progress = mod.listPokemonProgress() || { badges: [], dexes: [] };
    var rows = [];
    if (progress.hasPokedex != null) rows.push('<label class="tm-pokemon-toggle"><span>' + esc(tr("拥有图鉴")) + '</span>' +
      '<input class="tm-switch" type="checkbox" data-action="pokemon-progress" data-kind="pokedex" data-index="-1" ' + (progress.hasPokedex ? "checked" : "") + '></label>');
    (progress.dexes || []).forEach(function (dex) {
      rows.push('<label class="tm-pokemon-toggle"><span>' + esc(dex.name) + '</span><input class="tm-switch" type="checkbox" data-action="pokemon-progress" data-kind="regionalDex" data-index="' + dex.index + '" ' +
        (dex.unlocked ? "checked" : "") + '></label>');
    });
    (progress.badges || []).forEach(function (badge) {
      rows.push('<label class="tm-pokemon-toggle"><span>' + esc(tr("徽章") + " " + (Number(badge.index) + 1)) + '</span><input class="tm-switch" type="checkbox" data-action="pokemon-progress" data-kind="badge" data-index="' + badge.index + '" ' +
        (badge.obtained ? "checked" : "") + '></label>');
    });
    var html = rows.length ? card("徽章与图鉴", (progress.badges && progress.badges.length ? '<div class="tm-row">' + button("获得全部徽章", "all-badges", "primary", 'data-obtained="1"') +
      button("移除全部徽章", "all-badges", "", 'data-obtained="0"') + '</div>' : "") + rows.join("")) :
      '<div class="tm-note">' + esc(tr("当前游戏没有公开徽章或图鉴接口")) + '</div>';
    content.innerHTML = html;
  }
  function openAddPokemon() {
    addPokemonQuery = "";
    modal("获得宝可梦", '<div class="tm-row">' + input("tm-add-pokemon-query", "", "search", 'placeholder="' + esc(tr("搜索宝可梦名称或编号")) + '"') +
      input("tm-add-pokemon-level", 5, "number", 'min="1" max="100" step="1" aria-label="' + esc(tr("等级")) + '"') + button("搜索", "search-add-pokemon", "primary") + '</div>' +
      '<div id="tm-add-pokemon-list" class="tm-pokemon-choice-list"></div>');
    loadAddPokemonCandidates();
  }
  function loadAddPokemonCandidates() {
    var list = panel && panel.querySelector("#tm-add-pokemon-list"); if (!list) return;
    var result = mod.listDatabase("pokemon", addPokemonQuery, 0, 100) || { values: [] };
    list.innerHTML = (result.values || []).map(function (entry) {
      return '<div class="tm-item"><span class="tm-name">' + esc(entry.name || entry.id) + '</span><span class="tm-badge">#' + esc(entry.id) + '</span>' +
        button("获得", "add-pokemon", "primary", 'data-key="' + esc(entry.id) + '"') + '</div>';
    }).join("") || '<div class="tm-note">' + esc(tr("没有匹配宝可梦")) + '</div>';
  }
  function valueEditor(index, key, label, current) {
    var id = "tm-pokemon-value-" + index + "-" + key;
    return '<label class="tm-pokemon-value"><span>' + esc(tr(label)) + '</span>' + input(id, current, "number", 'min="' + (key === "eggSteps" ? "1" : "0") + '" step="1" inputmode="numeric"') +
      button("设置", "set-pokemon-value", "", 'data-index="' + index + '" data-field="' + key + '" data-input="' + id + '"') + '</label>';
  }
  function statusName(status) {
    var names = { SLEEP: "睡眠", POISON: "中毒", BURN: "灼伤", PARALYSIS: "麻痹", FROZEN: "冰冻" };
    return names[String(status || "").toUpperCase()] || status;
  }
  function renderBag(content) {
    if (!hasFeature("itemBag")) { content.innerHTML = '<div class="tm-note">' + esc(tr("背包能力正在初始化")) + '</div>'; return; }
    if (!hasFeature("itemBagReady")) { content.innerHTML = '<div class="tm-note">' + esc(tr("开始新游戏或载入存档后即可读取背包")) + '</div>'; return; }
    try { bagCategories = mod.listBagCategories() || []; } catch (_) { bagCategories = []; }
    if (bagCategory && !bagCategories.some(function (entry) { return entry.id === bagCategory; })) bagCategory = "";
    var categoryButtons = '<button class="tm-btn ' + (!bagCategory ? "primary" : "") + '" data-action="bag-category" data-category="">' + esc(tr("全部")) + '</button>' +
      bagCategories.map(function (entry) { return '<button class="tm-btn ' + (bagCategory === entry.id ? "primary" : "") + '" data-action="bag-category" data-category="' + esc(entry.id) + '">' + esc(entry.name) + '</button>'; }).join("");
    var controls = '<div class="tm-pokemon-categories">' + categoryButtons + '</div><div class="tm-row">' + input("tm-bag-query", itemQuery, "search", 'placeholder="' + esc(tr("搜索物品名称或编号")) + '"') +
      button("搜索", "search-bag", "primary") + '</div><div class="tm-row tm-pokemon-pager"><label><input class="tm-switch" type="checkbox" data-action="show-empty-items" ' +
      (showEmptyItems ? "checked" : "") + '> ' + esc(tr("显示未持有物品")) + '</label><span class="tm-grow" id="tm-bag-total"></span>' +
      button("上一页", "bag-prev") + button("下一页", "bag-next") + '</div>';
    var header = '<div class="tm-lock-list-header tm-item-lock-header"><span>' + esc(tr("锁定")) + '</span><span>' + esc(tr("物品")) + '</span><span>' + esc(tr("数量")) + '</span></div>';
    content.innerHTML = '<div class="tm-grid tm-item-layout">' + card("背包", controls + header + '<div id="tm-bag-list" class="tm-list"></div>', "tm-item-roster") +
      '<section class="tm-card tm-item-detail" id="tm-bag-detail"></section></div>';
    loadBag();
  }
  function loadBag() {
    var list = panel && panel.querySelector("#tm-bag-list"); if (!list) return;
    var result = mod.listDatabase("item", itemQuery, itemOffset, 100, bagCategory) || { total: 0, values: [] };
    var entries = result.values || [];
    if (!showEmptyItems) entries = entries.filter(function (item) { return Number(item.count) > 0; });
    if (!entries.some(function (item) { return String(item.id) === String(selectedItemId); })) selectedItemId = entries.length ? String(entries[0].id) : null;
    list.innerHTML = entries.map(function (item) {
      var id = String(item.id); var selected = id === String(selectedItemId);
      return '<div class="tm-item tm-lock-row tm-item-lock-row' + (selected ? ' selected' : '') + '"><label class="tm-lock-cell" aria-label="' + esc(tr("锁定") + " #" + id + " " + item.name) + '"><input class="tm-lock-checkbox" type="checkbox" data-action="lock-item" data-id="' + esc(id) + '" ' +
        (item.locked ? "checked" : "") + '></label><button type="button" class="tm-item-select" data-action="select-bag-item" data-id="' + esc(id) + '" aria-pressed="' + (selected ? "true" : "false") + '"><span class="tm-name">#' + esc(id) + ' ' + esc(item.name || id) + '</span><span class="tm-badge tm-item-count">' + esc(item.count || 0) + '</span></button></div>';
    }).join("") || '<div class="tm-note">' + esc(tr(itemQuery ? "没有匹配物品" : "背包里没有物品；搜索名称可添加物品")) + '</div>';
    var total = panel.querySelector("#tm-bag-total"); if (total) total.textContent = tr("共") + " " + String(result.total || 0) + " " + tr("项");
    renderBagDetail(entries);
  }
  function renderBagDetail(entries) {
    var detail = panel && panel.querySelector("#tm-bag-detail");
    if (!detail) return;
    var item = (entries || []).filter(function (entry) { return String(entry.id) === String(selectedItemId); })[0];
    if (!item) { detail.innerHTML = '<h3>' + esc(tr("物品详情")) + '</h3><div class="tm-note">' + esc(tr("请选择背包物品查看详情")) + '</div>'; return; }
    var description = String(item.description || "").trim() || "—";
    detail.innerHTML = '<div class="tm-item-detail-heading"><h3>' + esc(tr("物品详情")) + '</h3><span class="tm-badge">#' + esc(item.id) + '</span></div>' +
      '<div class="tm-item-detail-info"><div class="tm-item-detail-name"><span>' + esc(tr("名称：")) + '</span><strong>' + esc(item.name || "—") + '</strong></div>' +
      '<div class="tm-item-detail-description"><span>' + esc(tr("说明：")) + '</span><p>' + esc(description) + '</p></div></div>' +
      '<section class="tm-item-quantity-card"><div class="tm-item-quantity-heading"><h4>' + esc(tr("数量操作")) + '</h4><div class="tm-item-current-count"><span>' + esc(tr("当前数量")) + '</span><strong>' + esc(item.count || 0) + '</strong></div></div>' +
      '<div class="tm-item-quantity-control">' + input("tm-detail-count", item.count || 0, "number", 'min="0" max="999999" inputmode="numeric"') + button("应用", "set-item-count", "primary", 'data-id="' + esc(item.id) + '" data-input="tm-detail-count"') + '</div></section>';
  }
  function renderSystem(content) {
    if (!hasFeature("systemData")) { content.innerHTML = '<div class="tm-note">' + esc(tr("游戏开关/变量能力正在初始化")) + '</div>'; return; }
    var kinds = [["switch", "开关"], ["variable", "变量"]];
    var selectedKind = tr(systemKind === "switch" ? "开关" : "变量");
    var options = kinds.map(function (entry) {
      var active = systemKind === entry[0];
      return '<button type="button" class="tm-system-kind-option' + (active ? ' active' : '') + '" role="option" aria-selected="' + (active ? 'true' : 'false') + '" data-action="select-system-kind" data-kind="' + entry[0] + '">' + esc(tr(entry[1])) + '</button>';
    }).join("");
    var kind = '<div class="tm-system-kind-picker"><button type="button" class="tm-system-kind-trigger" data-action="toggle-system-kind" aria-haspopup="listbox" aria-expanded="' + (systemKindExpanded ? 'true' : 'false') + '"><span>' + esc(selectedKind) + '</span><span class="tm-system-kind-arrow" aria-hidden="true">▾</span></button>' +
      (systemKindExpanded ? '<div class="tm-system-kind-menu" role="listbox">' + options + '</div>' : '') + '</div>';
    var header = '<div class="tm-lock-list-header tm-system-lock-header"><span>' + esc(tr("锁定")) + '</span><span>' + esc(tr("名称")) + '</span><span>' + esc(tr("当前值")) + '</span></div>';
    content.innerHTML = card("开关与变量", '<div class="tm-row">' + kind + input("tm-system-query", systemQuery, "search", 'placeholder="' + esc(tr("名称或 ID")) + '"') + '</div>' + header + '<div id="tm-system-list" class="tm-list"></div>', "tm-fill-card",
      '<label class="tm-hide-empty-toggle"><input class="tm-switch" type="checkbox" data-action="toggle-hide-empty-system" ' + (hideEmptySystemEntries ? "checked" : "") + '><span>' + esc(tr("隐藏空选项")) + '</span></label>');
    loadSystem();
  }
  function renderMap(content) {
    var position = {};
    try { position = mod.currentPosition() || {}; } catch (_) {}
    mapListExpanded = false;
    currentMapId = Number(position.mapId) || 0;
    mapId = currentMapId;
    eventId = 0;
    selectedEventDetail = null;
    var currentMapLabel = mapLabel(mapId);
    var mapPicker = '<button class="tm-btn tm-map-picker" data-action="toggle-map-list" title="' + esc(currentMapLabel) + '"><span class="tm-map-picker-label">' + esc(currentMapLabel) + '</span><span class="tm-map-picker-arrow" aria-hidden="true">▾</span></button>';
    var browseRow = (hasFeature("mapControl") ? '<div class="tm-map-picker-row">' + mapPicker + '</div>' : '') +
      ((hasFeature("mapControl") || hasFeature("events")) ? '<div class="tm-row">' +
        input("tm-browse-query", "", "search", 'placeholder="' + esc(tr("搜索")) + '"') + button("搜索", "render-browse", "primary") + '</div>' : '');
    var mapControls = (hasFeature("mapControl") ? '<div class="tm-row tm-map-teleport-row">' +
      input("tm-pokemon-map-id", position.mapId, "number", 'placeholder="' + esc(tr("地图 ID")) + '"') +
      input("tm-pokemon-map-x", position.x, "number", 'placeholder="X"') + input("tm-pokemon-map-y", position.y, "number", 'placeholder="Y"') +
      button("传送", "teleport", "primary") + '</div>' : '') +
      ((hasFeature("minimap") || hasFeature("eventSelfSwitches")) ? '<div class="tm-map-action-buttons">' +
        (hasFeature("minimap") ? button("迷你地图", "show-minimap") : '') +
        (hasFeature("eventSelfSwitches") ? button("事件开关全开", "map-selfswitches", "", 'data-enabled="1"') + button("事件开关全关", "map-selfswitches", "", 'data-enabled="0"') : '') +
        '</div>' : '');
    content.innerHTML = '<div class="tm-grid tm-map-grid">' +
      ((hasFeature("mapControl") || hasFeature("events")) ? '<section class="tm-card tm-browse-card"><h3>' + esc(tr("浏览")) + '</h3>' + browseRow +
        (hasFeature("events") ? '<div class="tm-list" id="tm-browse-list"></div>' : '') +
        (hasFeature("mapControl") ? '<div class="tm-list tm-map-dropdown" id="tm-map-list" style="display:none"></div>' : '') + '</section>' : '') +
      (mapControls ? card("地图操作", mapControls) : '') +
      (hasFeature("events") ? card("事件详情", '<div id="tm-event-panel">' + eventPanelHtml() + '</div>') : '') + '</div>';
    loadEventsForMap(mapId);
    loadBrowse();
    renderEventPanel();
  }
  function mapLabel(targetMapId) {
    var label = "#" + ("0000" + (Number(targetMapId) || 0)).slice(-4);
    if (!targetMapId || !hasFeature("mapControl")) return label;
    try {
      var maps = mod.listMaps(String(targetMapId), 0, 300).values || [];
      var map = maps.filter(function (entry) { return Number(entry.id) === Number(targetMapId); })[0];
      if (map && map.name) label += ": " + map.name;
    } catch (_) {}
    return label;
  }
  function loadEventsForMap(targetMapId) {
    eventEntries = [];
    if (!hasFeature("events")) return;
    try { eventEntries = (mod.listEvents(Number(targetMapId) || 0) || {}).values || []; } catch (_) {}
  }
  function fillTeleport(targetMapId, x, y) {
    var mapInput = panel && panel.querySelector("#tm-pokemon-map-id");
    var xInput = panel && panel.querySelector("#tm-pokemon-map-x");
    var yInput = panel && panel.querySelector("#tm-pokemon-map-y");
    if (mapInput && targetMapId != null) mapInput.value = targetMapId;
    if (xInput && x != null) xInput.value = x;
    if (yInput && y != null) yInput.value = y;
  }
  function coordinateOrZero(value) {
    var coordinate = Number(value);
    return Number.isFinite(coordinate) ? coordinate : 0;
  }
  function loadBrowse() {
    var queryInput = panel && panel.querySelector("#tm-browse-query");
    if (queryInput) queryInput.placeholder = mapListExpanded ? tr("搜索地图名称 / ID") : tr("搜索 ID / 名称");
    var picker = panel && panel.querySelector(".tm-map-picker");
    if (picker) {
      var label = mapLabel(mapId);
      var labelNode = picker.querySelector(".tm-map-picker-label");
      if (labelNode) labelNode.textContent = label;
      picker.title = label;
      picker.classList.toggle("primary", mapListExpanded);
      var arrow = picker.querySelector(".tm-map-picker-arrow");
      if (arrow) arrow.textContent = mapListExpanded ? "▴" : "▾";
    }
    var list = panel && panel.querySelector("#tm-map-list");
    if (mapListExpanded && hasFeature("mapControl")) {
      renderMapList();
      if (list) {
        var eventList = panel.querySelector("#tm-browse-list");
        list.style.top = (eventList ? eventList.offsetTop : 124) + "px";
        list.style.display = "flex";
      }
    } else {
      if (list) list.style.display = "none";
      renderEventList();
    }
  }
  function renderMapList() {
    var list = panel && panel.querySelector("#tm-map-list");
    if (!list) return;
    if (!hasFeature("mapControl")) { list.innerHTML = ''; return; }
    list.classList.add("tm-map-tree-list");
    var position = {};
    try { position = mod.currentPosition() || {}; } catch (_) {}
    try { mapEntries = (mod.listMaps(value("tm-browse-query"), 0, 300) || {}).values || []; }
    catch (error) { list.innerHTML = '<div class="tm-note">' + esc(error.message || String(error)) + '</div>'; return; }
    var depths = mapEntries.map(function (entry) { return Math.max(0, Number(entry.depth) || 0); });
    var maxDepth = depths.reduce(function (max, depth) { return Math.max(max, depth); }, 0);
    var nextAtOrAbove = [];
    for (var level = 0; level <= maxDepth; level++) {
      var nextIndex = -1;
      var nextByRow = new Array(mapEntries.length);
      for (var row = mapEntries.length - 1; row >= 0; row--) {
        nextByRow[row] = nextIndex;
        if (depths[row] <= level) nextIndex = row;
      }
      nextAtOrAbove[level] = nextByRow;
    }
    function hasLaterAtLevel(index, level) {
      var next = nextAtOrAbove[level][index];
      return next >= 0 && depths[next] === level;
    }
    function treePrefix(index) {
      var depth = depths[index];
      var prefix = "";
      for (var level = 0; level < depth; level++) {
        prefix += '<span class="tm-map-tree-guide' + (hasLaterAtLevel(index, level) ? " tm-guide-continuing" : "") + '"></span>';
      }
      return depth ? prefix + '<span class="tm-map-tree-branch">' + (hasLaterAtLevel(index, depth) ? "├─" : "└─") + '</span>' : "";
    }
    list.innerHTML = mapEntries.map(function (entry, index) {
      var here = Number(position.mapId) === Number(entry.id);
      var picked = Number(mapId) === Number(entry.id);
      var paddedId = ("0000" + entry.id).slice(-4);
      var name = '<span class="tm-map-tree-prefix">' + treePrefix(index) + '</span><span class="tm-map-tree-label">#' + paddedId + ": " + esc(entry.name || "") + '</span>';
      return '<div class="tm-item' + (picked ? " tm-active" : "") + '"><span class="tm-name tm-map-name">' + name + '</span>' +
        (here ? '<span class="tm-badge tm-on">' + esc(tr("你在这里")) + '</span>' : "") + button("选择", "pick-map", "", 'data-id="' + esc(entry.id) + '"') + '</div>';
    }).join("") || '<div class="tm-note">' + esc(tr("没有匹配地图")) + '</div>';
    var active = list.querySelector(".tm-item .tm-badge.tm-on") || list.querySelector(".tm-item.tm-active");
    if (active && active.scrollIntoView) { try { active.closest(".tm-item").scrollIntoView({ block: "center" }); } catch (_) {} }
  }
  function renderEventList() {
    var list = panel && panel.querySelector("#tm-browse-list");
    if (!list) return;
    if (!hasFeature("events")) { list.innerHTML = '<div class="tm-note">' + esc(tr("事件列表对当前游戏不可用")) + '</div>'; return; }
    var query = String(value("tm-browse-query") || "").toLowerCase();
    var filtered = eventEntries.filter(function (entry) {
      return !query || String(entry.name || "").toLowerCase().indexOf(query) >= 0 || String(entry.id).indexOf(query) >= 0;
    });
    list.innerHTML = filtered.map(function (entry) {
      return '<div class="tm-item' + (String(eventId) === String(entry.id) ? " tm-active" : "") + '"><span class="tm-name">#' + esc(entry.id) + " " + esc(entry.name || "") +
        '</span><span class="tm-badge">' + esc(entry.x) + ',' + esc(entry.y) + '</span>' + button("选择", "pick-event", "", 'data-id="' + esc(entry.id) + '"') + '</div>';
    }).join("") || '<div class="tm-note">' + esc(tr("所选地图没有事件")) + '</div>';
  }
  function eventConditionEntriesHtml(page) {
    var conditions = page.conditions || [];
    if (!conditions.length) return '<div class="tm-note">' + esc(tr("无条件")) + '</div>';
    return '<div class="tm-event-condition-list tm-event-condition-dialog-list">' + conditions.map(function (condition) {
      var current = condition.current === "true" ? tr("开") : condition.current === "false" ? tr("关") : condition.current;
      var target = condition.target === "true" ? tr("开") : condition.target === "false" ? tr("关") : condition.target;
      var label;
      if (condition.type === "switch") label = tr("开关") + " " + condition.id + " · " + (condition.name || "") + " = " + target;
      else if (condition.type === "variable") label = tr("变量") + " " + condition.id + " · " + (condition.name || "") + " " + target;
      else if (condition.type === "self_switch") label = tr("独立开关") + " " + condition.id + " = " + target;
      else if (condition.type === "item") label = tr("物品") + " " + condition.id + " · " + (condition.name || "") + " " + condition.target;
      else if (condition.type === "actor") label = tr("角色") + " " + condition.id + " · " + (condition.name || "") + " " + tr(condition.current === "true" ? "在队伍中" : "不在队伍中");
      else label = (condition.name || condition.type) + " " + condition.target;
      var toggleable = condition.type === "switch" || condition.type === "self_switch";
      var tag = toggleable ? "button" : "div";
      var attrs = toggleable ? ' type="button" data-action="toggle-event-condition" data-condition-type="' + esc(condition.type) +
        '" data-id="' + esc(condition.id) + '" data-enabled="' + (condition.current === "true" ? "0" : "1") + '" data-page="' + esc(page.index) + '"' : "";
      return '<' + tag + ' class="tm-badge ' + (condition.met ? "tm-on" : "tm-off") + (toggleable ? " tm-condition-toggle" : "") +
        '" title="' + esc(tr("当前：") + current) + '"' + attrs + '>' + esc(label) + " · " + esc(current) + '</' + tag + '>';
    }).join("") + '</div>';
  }
  function eventPanelHtml() {
    if (!eventId) {
      selectedEventDetail = null;
      return '<div class="tm-note tm-event-empty">' + esc(tr("在左侧选择事件查看详情")) + '</div>';
    }
    var detail;
    try { detail = mod.eventDetail(eventId, mapId); }
    catch (error) { selectedEventDetail = null; return '<div class="tm-note tm-event-empty">' + esc(error.message || String(error)) + '</div>'; }
    if (!detail || detail.error) { selectedEventDetail = null; return '<div class="tm-note tm-event-empty">' + esc(tr("无法读取所选事件详情")) + '</div>'; }
    selectedEventDetail = detail;
    var pages = (detail.pages || []).map(function (page) {
      return '<div class="tm-event-page"><div class="tm-event-page-head"><strong>' + esc(tr("事件页 ") + (Number(page.index) + 1)) + '</strong>' +
        '<span>' + esc(tr("指令数 ") + page.commandCount + " · " + tr("条件数 ") + page.validCount) + '</span><div class="tm-event-page-actions">' +
        button(tr("事件页开关") + " (" + (page.conditions || []).length + ")", "show-event-conditions", "", 'data-page="' + esc(page.index) + '"') +
        button(tr("事件解释器按钮"), "show-event-interpreter", "", 'data-page="' + esc(page.index) + '"') + '</div></div></div>';
    }).join("") || '<div class="tm-note">' + esc(tr("事件没有可显示的页面")) + '</div>';
    var description = String(detail.description || "").trim();
    return '<div class="tm-event-summary"><div class="tm-event-overview"><span class="tm-event-name-label">' + esc(tr("名称：")) +
      '</span><strong title="' + esc(detail.name || "") + '">' + esc(detail.name || "") + '</strong><span class="tm-event-pos">Pos: ' + esc(detail.x) + ", " + esc(detail.y) +
      '</span></div><div class="tm-event-description"><small>' + esc(tr("描述：")) + '</small><span title="' + esc(description || "—") + '">' + esc(description || "—") +
      '</span></div></div><div class="tm-event-pages">' + pages + '</div>';
  }
  function renderEventPanel() {
    var target = panel && panel.querySelector("#tm-event-panel");
    if (target) target.innerHTML = eventPanelHtml();
  }
  function eventCommandName(code) {
    var names = { 0: "结束", 101: "显示文本", 102: "显示选项", 103: "数值输入", 104: "物品选择", 105: "滚动文本", 108: "注释", 111: "条件分支", 112: "循环", 113: "中断循环", 115: "事件处理结束", 117: "调用公共事件", 118: "标签", 119: "跳转标签", 121: "控制开关", 122: "控制变量", 123: "控制独立开关", 124: "控制定时器", 125: "增减金钱", 126: "增减物品", 127: "增减武器", 128: "增减护甲", 129: "更改队伍", 201: "传送玩家", 202: "设置事件位置", 203: "设置地图滚动", 204: "更改地图设置", 205: "设置移动路线", 206: "移动路线结束", 211: "显示动画", 212: "显示气泡图标", 213: "事件透明度", 221: "淡出画面", 222: "淡入画面", 223: "更改画面色调", 224: "画面闪烁", 225: "画面震动", 230: "等待", 231: "显示图片", 232: "移动图片", 235: "移除图片", 241: "播放 BGM", 245: "播放 BGS", 249: "播放 ME", 250: "播放 SE", 251: "停止 SE", 301: "战斗处理", 302: "商店处理", 303: "名称输入", 311: "增减 HP", 312: "增减 MP", 313: "更改状态", 314: "全体恢复", 315: "增减经验值", 316: "增减等级", 317: "增减能力值", 318: "增减技能", 319: "更改装备", 320: "更改角色名称", 321: "更改职业", 322: "更改角色图像", 323: "更改载具图像", 331: "增减敌人 HP", 332: "增减敌人 MP", 333: "更改敌人状态", 334: "敌人全体恢复", 335: "敌人出现", 336: "敌人变身", 337: "显示战斗动画", 339: "强制行动", 340: "中断战斗", 351: "打开菜单", 352: "打开存档画面", 353: "游戏结束", 355: "脚本", 401: "文本内容", 402: "选项分支", 403: "取消分支", 404: "结束选项分支", 405: "滚动文本内容", 408: "注释行", 412: "结束条件分支", 413: "重复循环", 601: "胜利时", 602: "逃跑时", 603: "失败时", 604: "结束战斗分支", 655: "脚本行" };
    return tr(names[Number(code)] || "事件指令");
  }
  function showEventConditionsDialog(pageIndex) {
    if (!selectedEventDetail) return;
    var page = (selectedEventDetail.pages || []).filter(function (entry) { return Number(entry.index) === Number(pageIndex); })[0];
    if (!page) return;
    modal("事件条件", eventConditionEntriesHtml(page), {
      boxClass: "tm-conditions-modal",
      headerSuffix: '<span class="tm-interpreter-page">' + esc(tr("事件页 ") + (Number(page.index) + 1)) + '</span>',
      closeLabel: "×", hint: tr("点击开关或独立开关条件可单独切换")
    });
  }
  function showEventInterpreterDialog(pageIndex, stack) {
    if (!selectedEventDetail) return;
    var detail = selectedEventDetail;
    var page = (detail.pages || []).filter(function (entry) { return Number(entry.index) === Number(pageIndex); })[0];
    if (!page) return;
    var commands;
    try { commands = mod.eventInterpreter(detail.id, detail.mapId, page.index) || []; }
    catch (error) { return modal("事件解释器", '<div class="tm-note">' + esc(error.message || String(error)) + '</div>', {
      boxClass: "tm-interpreter-modal", headerSuffix: '<span class="tm-interpreter-page">' + esc(tr("事件页 ") + (Number(page.index) + 1)) + '</span>', closeLabel: "×", stack: !!stack
    }); }
    var rows = commands.map(function (command) {
      var indent = Math.max(0, Math.min(8, Number(command.indent) || 0));
      var preview = String(command.preview || "").trim();
      return '<details class="tm-interpreter-command" style="margin-left:' + (indent * 8) + 'px"><summary><span class="tm-command-code">' + esc(command.code) +
        '</span><strong>' + esc(eventCommandName(command.code)) + '</strong>' + (preview ? '<span class="tm-command-preview" title="' + esc(preview) + '">' + esc(preview) + '</span>' : "") +
        '<span class="tm-command-index">#' + esc(command.index) + '</span></summary><pre>' + esc(command.parameters || "（无参数）") + '</pre></details>';
    }).join("") || '<div class="tm-note">' + esc(tr("此事件页没有指令")) + '</div>';
    modal("事件解释器", rows, {
      boxClass: "tm-interpreter-modal", bodyClass: "tm-interpreter-list",
      headerSuffix: '<span class="tm-interpreter-page">' + esc(tr("事件页 ") + (Number(page.index) + 1)) + '</span>',
      closeLabel: "×", hint: tr("点击指令可展开查看参数"), stack: !!stack
    });
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
    if (!data || !(Number(data.width) > 0) || !(Number(data.height) > 0)) return toast(tr("无法读取当前地图"), true);
    var legend = '<div class="tm-map-legend"><span class="tm-lg"><i class="tm-map-legend-swatch tm-blocked"></i>' + esc(tr("无法通行")) + '</span>' +
      '<span class="tm-lg"><i class="tm-map-legend-swatch tm-passable"></i>' + esc(tr("可通行")) + '</span>' +
      '<span class="tm-lg"><i class="tm-map-legend-swatch tm-event"></i>' + esc(tr("事件")) + '</span>' +
      '<span class="tm-lg"><i class="tm-map-legend-swatch tm-player"></i>' + esc(tr("你")) + '</span>' +
      '<span class="tm-lg"><i class="tm-map-legend-swatch tm-movable"></i>' + esc(tr("敌人/可移动事件")) + '</span>' +
      '<span class="tm-lg"><i class="tm-map-legend-swatch tm-item-event"></i>' + esc(tr("宝箱/道具事件")) + '</span>' +
      '<span class="tm-lg"><i class="tm-map-legend-swatch tm-teleport"></i>' + esc(tr("传送点")) + '</span></div>';
    var body = '<div class="tm-minimap-viewport"><canvas id="tm-pokemon-minimap" class="tm-minimap"></canvas></div>' +
      '<div class="tm-minimap-controls"><div class="tm-minimap-action-card"><span class="tm-minimap-selection" id="tm-pokemon-minimap-selection">' + esc(tr("点击地图选择格子")) + '</span>' +
      '<div class="tm-minimap-actions">' + button(tr("传送"), "minimap-cell-teleport", "primary", "disabled") +
      button(tr("事件解释器"), "minimap-cell-event-interpreter", "", "disabled") + '</div></div>' +
      '<div class="tm-minimap-zoom-card"><div class="tm-minimap-control-head"><span>' + esc(tr("地图大小")) + '</span><strong class="tm-minimap-zoom-value">100%</strong></div>' +
      '<div class="tm-minimap-slider-row"><span>' + esc(tr("缩小")) + '</span><input id="tm-pokemon-minimap-zoom" type="range" min="100" max="400" step="5" value="100" aria-label="' + esc(tr("地图大小")) + '">' +
      '<span>' + esc(tr("放大")) + '</span></div></div></div>';
    var dialog = modal(tr("迷你地图 #") + data.mapId + " · " + data.width + "×" + data.height, body, {
      headerSuffix: legend, closeLabel: "×", hint: tr("点击格子选中坐标；再用下方按钮传送或查看事件；按住拖动地图可平移；点击遮罩或 × 关闭"),
      boxClass: "tm-minimap-modal-box", bodyClass: "tm-minimap-modal-body",
      onClose: function () { renderEventPanel(); }
    });
    var canvas = dialog && dialog.querySelector("#tm-pokemon-minimap");
    if (!canvas) return;
    dialog._miniMapRenderer = window.TyranorMiniMap && window.TyranorMiniMap.mount({
      canvas: canvas,
      viewport: canvas.parentElement,
      slider: dialog.querySelector("#tm-pokemon-minimap-zoom"),
      zoomValue: dialog.querySelector(".tm-minimap-zoom-value"),
      data: data,
      onCell: function (x, y, events) {
        minimapSelection = { mapId: Number(data.mapId) || currentMapId, x: x, y: y, events: events || [] };
        var selectionLabel = dialog.querySelector("#tm-pokemon-minimap-selection");
        var actions = dialog.querySelectorAll(".tm-minimap-actions [data-action]");
        if (selectionLabel) selectionLabel.textContent = tr("已选格") + " (" + x + ", " + y + ")";
        if (actions[0]) actions[0].disabled = false;
        if (actions[1]) actions[1].disabled = false;
      },
      onClear: function () {
        minimapSelection = null;
        var selectionLabel = dialog.querySelector("#tm-pokemon-minimap-selection");
        var actions = dialog.querySelectorAll(".tm-minimap-actions [data-action]");
        if (selectionLabel) selectionLabel.textContent = tr("点击地图选择格子");
        Array.prototype.forEach.call(actions, function (action) { action.disabled = true; });
      }
    });
  }
  function showMinimapEventInterpreterPicker(selectedId) {
    var selection = minimapSelection;
    if (!selection) return;
    if (!selection.events.length) return toast(tr("该格没有事件"));
    var candidates = selection.events;
    if (selectedId != null) candidates = candidates.filter(function (entry) { return Number(entry.id) === Number(selectedId); });
    if (selectedId == null && candidates.length === 1) selectedId = candidates[0].id;
    if (selectedId == null) {
      var eventRows = candidates.map(function (entry) {
        var known = eventEntries.filter(function (value) { return Number(value.id) === Number(entry.id); })[0];
        return '<div class="tm-item"><span class="tm-name">#' + esc(entry.id) + ' ' + esc(known && known.name || "") + '</span>' +
          button("事件解释器", "minimap-pick-event", "", 'data-id="' + esc(entry.id) + '"') + '</div>';
      }).join("");
      modal("选择事件", '<div class="tm-map-event-picker">' + eventRows + '</div>', { closeLabel: "×", stack: true });
      return;
    }
    if (!candidates.length) return;
    var selected = candidates[0];
    var detail;
    try { detail = mod.eventDetail(Number(selected.id), selection.mapId); }
    catch (error) { toast(error.message || String(error), true); return; }
    if (!detail || detail.error || !detail.pages || !detail.pages.length) return toast(tr("事件没有可显示的页面"), true);
    eventId = Number(selected.id);
    mapId = selection.mapId;
    selectedEventDetail = detail;
    if (detail.pages.length === 1) return showEventInterpreterDialog(detail.pages[0].index, true);
    var pageRows = detail.pages.map(function (page) {
      return '<div class="tm-item"><span class="tm-name">' + esc(tr("事件页 ") + (Number(page.index) + 1) + " · " + tr("指令数 ") + page.commandCount) + '</span>' +
        button("事件解释器", "minimap-open-event-page", "", 'data-page="' + esc(page.index) + '"') + '</div>';
    }).join("");
    modal(tr("事件解释器") + " · #" + detail.id + " " + (detail.name || ""), '<div class="tm-map-event-picker">' + pageRows + '</div>', { closeLabel: "×", stack: true });
  }
  function loadSystem() {
    var list = panel && panel.querySelector("#tm-system-list"); if (!list) return;
    systemQuery = value("tm-system-query");
    var includeUnnamed = !hideEmptySystemEntries;
    var result = systemKind === "switch" ? mod.listSwitches(systemQuery, 0, 200, includeUnnamed) : mod.listVariables(systemQuery, 0, 200, includeUnnamed);
    var entries = (result.values || []).filter(function (entry) {
      if (isDashPlaceholder(entry.name)) return false;
      return !hideEmptySystemEntries || entry.named !== false;
    });
    list.innerHTML = entries.map(function (entry) {
      var id = Number(entry.id);
      var name = String(entry.name || (systemKind === "switch" ? tr("开关 ") : tr("变量 ")) + entry.id);
      var isSwitch = systemKind === "switch";
      var control;
      if (isSwitch) {
        var switchOn = systemSwitchOn(entry.value);
        control = '<label class="tm-system-switch"><span class="tm-system-switch-state">' + esc(tr(switchOn ? "开" : "关")) + '</span><input class="tm-switch tm-system-switch-input" id="tm-system-' + safeId(id) + '" type="checkbox" data-action="set-system-switch" data-id="' + esc(id) + '" data-current-value="' + (switchOn ? "1" : "0") + '" ' + (switchOn ? "checked" : "") + '></label>';
      } else {
        var inputValue = typeof entry.value === "object" && entry.value !== null ? JSON.stringify(entry.value) : entry.value;
        control = '<input class="tm-input tm-system-value" id="tm-system-' + safeId(id) + '" type="text" data-action="set-system" data-id="' + esc(id) + '" value="' + esc(inputValue) + '" aria-label="' + esc(name) + '">';
      }
      return '<div class="tm-item tm-lock-row tm-system-lock-row' + (isSwitch ? ' tm-system-switch-row' : '') + '"><label class="tm-lock-cell"><input class="tm-lock-checkbox" type="checkbox" data-action="lock-system" data-kind="' + systemKind + '" data-id="' + esc(id) + '" ' + (entry.locked ? "checked" : "") + '></label><span class="tm-name" title="#' + esc(id) + ' ' + esc(name) + '">#' + esc(id) + ' ' + esc(name) + '</span>' + control + '</div>';
    }).join("") || '<div class="tm-note">' + esc(tr("没有匹配项目")) + '</div>';
  }
  function renderAbout(content) {
    content.innerHTML = card("宝可梦修改器", '<p>' + esc(tr("宝可梦专属修改面板")) + '</p><p>' +
      esc(tr("队伍、背包和游戏数据只通过当前 Essentials 游戏接口修改。")) + '</p><p>' + esc(tr("重要修改前建议先保存游戏。")) + '</p>');
  }
  function openPokemon() {
    if (panel) return;
    featureAttempts = 0;
    try { var initialCaps = mod.listCapabilities(); if (initialCaps && initialCaps.engine === "essentials") caps = initialCaps; }
    catch (error) { console.warn("Essentials capability load failed", error); }
    panel = document.createElement("div"); panel.id = "tyranor-mod-root";
    panel.innerHTML = '<div class="tm-panel" role="dialog" aria-modal="true"><div class="tm-head"><strong>' + esc(tr("宝可梦修改器")) +
      '</strong><button class="tm-refresh" data-action="refresh" title="' + esc(tr("刷新面板")) + '" aria-label="' + esc(tr("刷新面板")) + '">↻</button>' +
      '<button class="tm-close" data-action="close" aria-label="' + esc(tr("关闭")) + '">×</button></div><div class="tm-tabs"></div><main class="tm-content"></main></div>';
    document.documentElement.appendChild(panel);
    panel.addEventListener("click", handleClick);
    panel.addEventListener("change", handleChange);
    panel.addEventListener("input", function (event) {
      if (event.target.id === "tm-team-query") { teamQuery = event.target.value; renderTeamRoster(); }
      if (event.target.id === "tm-move-query") renderMoveCandidates();
      if (event.target.id === "tm-held-query") renderHeldCandidates();
      if (event.target.id === "tm-system-query") loadSystem();
      if (event.target.id === "tm-browse-query") loadBrowse();
    });
    panel.addEventListener("keydown", function (event) {
      if (event.key === "Escape") { event.preventDefault(); closePokemon(true); }
      else if (event.key === "Enter" && event.target.id === "tm-bag-query") { event.preventDefault(); itemQuery = value("tm-bag-query").trim(); itemOffset = 0; loadBag(); }
      else if (event.key === "Enter" && event.target.id === "tm-add-pokemon-query") { event.preventDefault(); addPokemonQuery = value("tm-add-pokemon-query").trim(); loadAddPokemonCandidates(); }
      else if (event.key === "Enter" && event.target.classList && event.target.classList.contains("tm-system-value")) { event.preventDefault(); event.target.blur(); }
    });
    panel.addEventListener("click", function (event) {
      if (mapListExpanded) {
        var target = event.target;
        var insidePicker = target.closest(".tm-map-picker, #tm-map-list");
        var insideMapSearch = target.closest("#tm-browse-query, [data-action='render-browse']");
        var pickingMap = target.closest("[data-action='pick-map']");
        if (!insidePicker && !insideMapSearch && !pickingMap) {
          mapListExpanded = false;
          var browseQuery = panel.querySelector("#tm-browse-query");
          if (browseQuery) browseQuery.value = "";
          loadBrowse();
        }
      }
      if (systemKindExpanded && !event.target.closest(".tm-system-kind-picker")) {
        systemKindExpanded = false;
        var systemMenu = panel.querySelector(".tm-system-kind-menu");
        var systemTrigger = panel.querySelector(".tm-system-kind-trigger");
        if (systemMenu) systemMenu.remove();
        if (systemTrigger) systemTrigger.setAttribute("aria-expanded", "false");
      }
      event.stopPropagation();
    });
    ["touchstart", "touchmove", "touchend", "pointerdown", "pointerup", "mousedown", "mouseup", "keyup", "wheel"].forEach(function (name) {
      panel.addEventListener(name, function (event) { event.stopPropagation(); });
    });
    render();
  }
  function modal(title, body, options) {
    options = options || {};
    var old = panel.querySelector(".tm-pokemon-modal"); if (old && !options.stack) closeModal(old);
    var node = document.createElement("div"); node.className = "tm-modal tm-pokemon-modal";
    var closeLabel = options.closeLabel || "关闭";
    var suffix = options.headerSuffix || "";
    var hint = options.hint ? '<div class="tm-modal-hint">' + esc(options.hint) + '</div>' : "";
    var boxClass = options.boxClass ? " " + options.boxClass : "";
    var bodyClass = options.bodyClass ? " " + options.bodyClass : "";
    node._onClose = options.onClose || null;
    node.innerHTML = '<div class="tm-modal-box' + boxClass + '"><div class="tm-modal-head"><strong>' + esc(tr(title)) + '</strong>' + suffix + button(closeLabel, "close-modal") +
      '</div><div class="tm-modal-body' + bodyClass + '">' + body + '</div>' + hint + '</div>';
    node.addEventListener("click", function (event) {
      if (event.target === node) {
        closeModal(node);
        event.stopPropagation();
      }
    });
    panel.appendChild(node);
    return node;
  }
  function askConfirmation(action, id, message) {
    pendingConfirmation = { action: action, id: id };
    modal("确认", '<p>' + esc(tr(message)) + '</p><div class="tm-row">' + button("取消", "close-modal") + button("确认", "confirm-pending", "danger") + '</div>');
  }
  function editNickname(index, nickname) {
    modal("修改昵称", '<div class="tm-row">' + input("tm-pokemon-nickname", nickname || "", "text", 'maxlength="32"') +
      button("保存", "save-pokemon-nickname", "primary", 'data-index="' + Number(index) + '"') + '</div>' +
      '<div class="tm-note">' + esc(tr("留空或输入原物种名称可清除昵称")) + '</div>');
  }
  function closeModal(source) {
    var node = source && source.closest ? source.closest(".tm-pokemon-modal") : source;
    if (!node && panel) {
      var modals = panel.querySelectorAll(".tm-pokemon-modal");
      node = modals.length ? modals[modals.length - 1] : null;
    }
    if (!node) return;
    if (node._miniMapRenderer) { node._miniMapRenderer.destroy(); node._miniMapRenderer = null; }
    node.remove();
    if (!node._modalClosed && node._onClose) { node._modalClosed = true; node._onClose(); }
  }
  function editMoves(index) {
    moveContext = { index: Number(index), slot: 0, candidates: mod.listPokemonMoveCandidates() || [] };
    var moves = mod.listPokemonMoves(index) || [];
    modal("编辑招式", '<div class="tm-pokemon-choice-list">' + moves.map(function (move) {
      var row = '<div class="tm-item"><span class="tm-name">' + esc(tr("招式位") + " " + (Number(move.slot) + 1) + " · " + (move.name || tr("空"))) + '</span>' +
        button("替换", "choose-move", "", 'data-index="' + index + '" data-slot="' + move.slot + '"') + button("清空", "clear-move", "", 'data-index="' + index + '" data-slot="' + move.slot + '"') + '</div>';
      if (move.key && hasFeature("pokemonMovePp")) {
        var ppId = "tm-move-pp-" + index + "-" + move.slot;
        row += '<div class="tm-pokemon-move-row"><label>' + esc(tr("PP")) + ' ' + input(ppId, move.pp == null ? 0 : move.pp, "number", 'min="0" max="' + Number(move.totalPp || 999) + '" step="1"') +
          (move.totalPp == null ? "" : " / " + esc(move.totalPp)) + '</label>' + button("设置", "set-move-pp", "", 'data-index="' + index + '" data-slot="' + move.slot + '" data-field="pp" data-max="' + Number(move.totalPp || 999) + '" data-input="' + ppId + '"') + '</div>';
        if (hasFeature("pokemonMovePpUp") && move.ppUp != null) {
          var ppUpId = "tm-move-ppup-" + index + "-" + move.slot;
          row += '<div class="tm-pokemon-move-row"><label>' + esc(tr("PP 上升")) + ' ' + input(ppUpId, move.ppUp, "number", 'min="0" max="3" step="1"') + '</label>' +
            button("设置", "set-move-pp", "", 'data-index="' + index + '" data-slot="' + move.slot + '" data-field="ppup" data-input="' + ppUpId + '"') + '</div>';
        }
      }
      return row;
    }).join("") + '</div>');
  }
  function editTraits(index) {
    var fields = { nature: "性格", ability: "特性", form: "形态", gender: "性别", shiny: "闪光", superShiny: "超闪光" };
    var traits = mod.listPokemonTraits(Number(index)) || [];
    var html = traits.map(function (trait) {
      return '<section class="tm-pokemon-trait"><strong>' + esc(tr(fields[trait.field] || trait.field)) + '</strong><span class="tm-pokemon-details">' +
        esc(tr("当前值")) + '：' + esc(trait.currentName) + '</span><div class="tm-pokemon-choice-list">' + (trait.options || []).map(function (option) {
          return button(option.name, "set-pokemon-trait", String(option.key) === String(trait.currentKey) ? "primary" : "",
            'data-index="' + index + '" data-field="' + esc(trait.field) + '" data-key="' + esc(option.key) + '"');
        }).join("") + '</div></section>';
    }).join("");
    modal("个体属性", html || '<div class="tm-note">' + esc(tr("当前宝可梦没有可修改的个体属性")) + '</div>');
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
      toast(tr("数值已修改"));
      if ((key.indexOf("iv_") === 0 || key.indexOf("ev_") === 0) && teamSection !== "iv_ev") editStats(index);
      else renderTeam(panel.querySelector(".tm-content"));
    });
  }
  function handleClick(event) {
    var tabNode = event.target.closest("[data-tab]");
    if (tabNode) { tab = tabNode.dataset.tab; render(); event.stopPropagation(); return; }
    var node = event.target.closest("[data-action]"); if (!node) return;
    if (node.type === "checkbox") return;
    event.preventDefault(); event.stopPropagation(); var action = node.dataset.action;
    if (action === "close") return close(true);
    if (action === "refresh") { try { caps = mod.listCapabilities(); render(); } catch (error) { toast(error.message || String(error), true); } return; }
    if (action === "close-modal") { pendingConfirmation = null; return closeModal(node); }
    if (action === "confirm-pending") {
      var pending = pendingConfirmation; pendingConfirmation = null; closeModal();
      if (!pending) return;
      if (pending.action === "load") return run(function () { return mod.loadGame(Number(pending.id)); }, function () { toast(tr("读取完成")); render(); });
      if (pending.action === "delete") return run(function () { return mod.deleteSave(Number(pending.id)); }, function () { toast(tr("存档已删除")); renderSave(panel.querySelector(".tm-content")); });
      if (pending.action === "release-pokemon") return run(function () {
        return mod.releasePokemon(Number(pending.id.box), Number(pending.id.slot));
      }, function () {
        toast(tr("宝可梦已释放"));
        try { caps = mod.listCapabilities() || caps; } catch (_) {}
        renderTabs(); renderTeam(panel.querySelector(".tm-content"));
      });
      if (pending.action === "reset-keys") {
        try { window.TyranorInputNative.resetToDefaults(); toast(tr("虚拟按键已恢复默认")); } catch (error) { toast(error.message || String(error), true); }
      }
      return;
    }
    if (action === "quick-section") { quickSection = node.dataset.section || "resources"; return renderQuick(panel.querySelector(".tm-content")); }
    if (action === "set-speed") {
      var movementSpeed = Number(value("tm-pokemon-speed"));
      if (!Number.isFinite(movementSpeed) || movementSpeed < 0.25 || movementSpeed > 8) return toast(tr("移动速度必须在 0.25 到 8 之间"), true);
      return run(function () { return mod.setSpeed(movementSpeed); }, function () { toast(tr("移动速度已设置")); renderQuick(panel.querySelector(".tm-content")); });
    }
    if (action === "set-game-speed") {
      var gameSpeed = Number(value("tm-pokemon-game-speed"));
      if (!Number.isInteger(gameSpeed) || gameSpeed < 1 || gameSpeed > 9) return toast(tr("游戏倍速必须在 1 到 9 之间"), true);
      return run(function () { return mod.setGameSpeed(gameSpeed); }, function () { toast(tr("游戏倍速已设置")); renderQuick(panel.querySelector(".tm-content")); });
    }
    if (action === "flag") return;
    if (action === "select-pokemon") { selectedPokemon = Number(node.dataset.index); return renderTeamRoster(); }
    if (action === "pokemon-section") { teamSection = node.dataset.section || "stats"; return renderTeam(panel.querySelector(".tm-content")); }
    if (action === "team-view") { teamView = node.dataset.view === "storage" ? "storage" : "party"; return renderTeam(panel.querySelector(".tm-content")); }
    if (action === "storage-prev" || action === "storage-next") {
      var currentBox = Number(node.dataset.box) || 0;
      var storageInfo = mod.listPokemonStorage(currentBox);
      if (!storageInfo) return toast(tr("无法读取宝可梦仓库"), true);
      storageBox = Math.max(0, Math.min(Number(storageInfo.boxCount) - 1, currentBox + (action === "storage-next" ? 1 : -1)));
      return renderTeam(panel.querySelector(".tm-content"));
    }
    if (action === "deposit-pokemon") {
      pendingDepositIndex = Number(node.dataset.index);
      teamView = "storage";
      return renderTeam(panel.querySelector(".tm-content"));
    }
    if (action === "cancel-deposit-pokemon") { pendingDepositIndex = null; return renderTeam(panel.querySelector(".tm-content")); }
    if (action === "store-selected-pokemon") return run(function () {
      return mod.movePokemonToStorage(Number(node.dataset.index), Number(node.dataset.box));
    }, function () {
      pendingDepositIndex = null; toast(tr("宝可梦已存入仓库"));
      try { caps = mod.listCapabilities() || caps; } catch (_) {}
      renderTabs(); renderTeam(panel.querySelector(".tm-content"));
    });
    if (action === "withdraw-pokemon") return run(function () {
      return mod.withdrawPokemonFromStorage(Number(node.dataset.box), Number(node.dataset.slot));
    }, function () {
      toast(tr("宝可梦已取出到队伍"));
      try { caps = mod.listCapabilities() || caps; } catch (_) {}
      renderTabs(); renderTeam(panel.querySelector(".tm-content"));
    });
    if (action === "confirm-release-pokemon") return askConfirmation("release-pokemon", {
      box: Number(node.dataset.box), slot: Number(node.dataset.slot),
    }, "确定释放这只宝可梦吗？此操作无法撤销。");
    if (action === "all-badges") return run(function () {
      return mod.setPokemonProgress("badgesAll", -1, node.dataset.obtained === "1");
    }, function () { toast(tr("徽章已更新")); renderProgress(panel.querySelector(".tm-content")); });
    if (action === "edit-nickname") return editNickname(node.dataset.index, node.dataset.name || "");
    if (action === "save-pokemon-nickname") return run(function () {
      return mod.setPokemonNickname(Number(node.dataset.index), value("tm-pokemon-nickname"));
    }, function () { closeModal(); toast(tr("昵称已修改")); renderTeam(panel.querySelector(".tm-content")); });
    if (action === "hatch-egg") return run(function () {
      return mod.setPokemonValue(Number(node.dataset.index), "eggSteps", "1");
    }, function () { toast(tr("蛋只需再走一步即可孵化")); renderTeam(panel.querySelector(".tm-content")); });
    if (action === "pokemon-progress") return;
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
    if (action === "open-add-pokemon") return openAddPokemon();
    if (action === "search-add-pokemon") { addPokemonQuery = value("tm-add-pokemon-query").trim(); return loadAddPokemonCandidates(); }
    if (action === "add-pokemon") {
      var level = Number(value("tm-add-pokemon-level"));
      if (!Number.isInteger(level) || level < 1 || level > 100) return toast(tr("请输入 1 到 100 的等级"), true);
      return run(function () { return mod.give("pokemon", node.dataset.key, level); }, function () {
        closeModal(); toast(tr("宝可梦已获得"));
        try { caps = mod.listCapabilities() || caps; } catch (_) {}
        renderTabs(); renderTeam(panel.querySelector(".tm-content"));
      });
    }
    if (action === "reorder-pokemon") return run(function () {
      return mod.setPokemonOrder(Number(node.dataset.index), Number(node.dataset.target));
    }, function () { toast(tr("队伍顺序已调整")); renderTeam(panel.querySelector(".tm-content")); });
    if (action === "edit-traits") return editTraits(node.dataset.index);
    if (action === "set-pokemon-trait") return run(function () {
      return mod.setPokemonTrait(Number(node.dataset.index), node.dataset.field || "", node.dataset.key || "");
    }, function () { closeModal(); toast(tr("个体属性已修改")); renderTeam(panel.querySelector(".tm-content")); });
    if (action === "set-pokemon-value") return setPokemonValue(node);
    if (action === "edit-moves") return editMoves(node.dataset.index);
    if (action === "set-move-pp") {
      var ppValue = String(value(node.dataset.input)).trim();
      var maxValue = node.dataset.field === "ppup" ? 3 : Number(node.dataset.max || 999);
      if (!/^\d+$/.test(ppValue) || Number(ppValue) > maxValue) return toast(tr(node.dataset.field === "ppup" ? "PP 上升值必须为 0 到 3" : "请输入有效的 PP 值"), true);
      return run(function () {
        return mod.setPokemonMovePp(moveContext.index, Number(node.dataset.slot), node.dataset.field, Number(ppValue));
      }, function () { toast(tr("招式 PP 已修改")); editMoves(moveContext.index); });
    }
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
    if (action === "bag-category") { bagCategory = node.dataset.category || ""; itemOffset = 0; return renderBag(panel.querySelector(".tm-content")); }
    if (action === "select-bag-item") { selectedItemId = String(node.dataset.id || ""); return loadBag(); }
    if (action === "bag-prev") { itemOffset = Math.max(0, itemOffset - 100); return loadBag(); }
    if (action === "bag-next") { itemOffset += 100; return loadBag(); }
    if (action === "set-item-count") {
      var quantity = Number(value(node.dataset.input)); if (!Number.isInteger(quantity) || quantity < 0 || quantity > 999999) return toast(tr("请输入 0 到 999999 的整数"), true);
      return run(function () { return mod.setItemCount("item", node.dataset.id, quantity); }, function () { toast(tr("数量已修改")); loadBag(); });
    }
    if (action === "toggle-system-kind") {
      systemKindExpanded = !systemKindExpanded;
      return renderSystem(panel.querySelector(".tm-content"));
    }
    if (action === "select-system-kind") {
      systemKind = node.dataset.kind === "variable" ? "variable" : "switch";
      systemKindExpanded = false;
      return renderSystem(panel.querySelector(".tm-content"));
    }
    if (action === "render-system") return loadSystem();
    if (action === "toggle-map-list") {
      mapListExpanded = !mapListExpanded;
      var mapSearch = panel.querySelector("#tm-browse-query");
      if (mapSearch) mapSearch.value = "";
      return loadBrowse();
    }
    if (action === "render-browse") return loadBrowse();
    if (action === "pick-map") {
      var selectedMapId = Number(node.dataset.id) || 0;
      var selectedMap = mapEntries.filter(function (entry) { return Number(entry.id) === selectedMapId; })[0];
      fillTeleport(selectedMapId, coordinateOrZero(selectedMap && selectedMap.currentX), coordinateOrZero(selectedMap && selectedMap.currentY));
      mapId = selectedMapId;
      eventId = 0;
      mapListExpanded = false;
      var browseQuery = panel.querySelector("#tm-browse-query");
      if (browseQuery) browseQuery.value = "";
      loadEventsForMap(mapId);
      loadBrowse();
      renderEventPanel();
      return;
    }
    if (action === "pick-event") {
      eventId = Number(node.dataset.id) || 0;
      var selectedEvent = eventEntries.filter(function (entry) { return Number(entry.id) === eventId; })[0];
      if (selectedEvent) fillTeleport(mapId, coordinateOrZero(selectedEvent.x), coordinateOrZero(selectedEvent.y));
      renderEventList();
      renderEventPanel();
      return;
    }
    if (action === "teleport") {
      var destinationMap = Number(value("tm-pokemon-map-id"));
      var destinationX = Number(value("tm-pokemon-map-x"));
      var destinationY = Number(value("tm-pokemon-map-y"));
      if (!Number.isInteger(destinationMap) || destinationMap < 1 || !Number.isInteger(destinationX) || destinationX < 0 || !Number.isInteger(destinationY) || destinationY < 0) return toast(tr("请输入有效的地图 ID 和坐标"), true);
      return run(function () { return mod.teleport(destinationMap, destinationX, destinationY, 2, false); }, function () {
        mapId = destinationMap; toast(tr("传送完成")); renderMap(panel.querySelector(".tm-content"));
      });
    }
    if (action === "map-selfswitches") return run(function () {
      return mod.setMapEventSelfSwitches(mapId || currentMapId, node.dataset.enabled === "1");
    }, function () {
      loadEventsForMap(mapId || currentMapId);
      renderEventList(); renderEventPanel();
      toast(node.dataset.enabled === "1" ? tr("所选地图事件开关已全开") : tr("所选地图事件开关已全关"));
    });
    if (action === "toggle-event-condition") {
      var conditionEnabled = node.dataset.enabled === "1";
      var conditionPage = node.dataset.page;
      var conditionsDialogOpen = !!panel.querySelector(".tm-conditions-modal");
      return run(function () {
        if (node.dataset.conditionType === "self_switch") return mod.setEventSelfSwitch(eventId, node.dataset.id, conditionEnabled, mapId);
        return mod.setSwitch(Number(node.dataset.id), conditionEnabled);
      }, function () {
        renderEventPanel();
        if (conditionsDialogOpen) showEventConditionsDialog(conditionPage);
        toast(tr("事件条件开关已更新"));
      });
    }
    if (action === "show-minimap") return showMinimap();
    if (action === "close-minimap") return closeModal(node);
    if (action === "close-cell-actions") { var cellDialog = node.closest(".tm-map-cell-modal"); if (cellDialog) cellDialog.remove(); return; }
    if (action === "minimap-cell-teleport") {
      var selection = minimapSelection;
      if (!selection) return;
      var cellOverlay = node.closest(".tm-map-cell-modal"); if (cellOverlay) cellOverlay.remove();
      var minimapDialog = node.closest(".tm-pokemon-modal");
      return run(function () { return mod.teleport(selection.mapId, selection.x, selection.y, 2, false); }, function () {
        mapId = selection.mapId; currentMapId = selection.mapId;
        toast(tr("传送完成"));
        renderMap(panel.querySelector(".tm-content"));
        window.setTimeout(function () {
          if (!minimapDialog || !minimapDialog.parentNode || !minimapDialog._miniMapRenderer) return;
          try {
            var updated = mod.minimapData();
            if (!updated || !minimapDialog._miniMapRenderer.updateData(updated)) return;
            var updatedMapId = Number(updated.mapId) || selection.mapId;
            if (updatedMapId !== selection.mapId) {
              var viewport = minimapDialog.querySelector(".tm-minimap-viewport");
              if (viewport) { viewport.scrollLeft = 0; viewport.scrollTop = 0; }
            }
            var heading = minimapDialog.querySelector(".tm-modal-head > strong");
            if (heading) heading.textContent = tr("迷你地图 #") + updatedMapId + " · " + updated.width + "×" + updated.height;
            if (minimapSelection && minimapSelection.x === selection.x && minimapSelection.y === selection.y) {
              if (selection.x < Number(updated.width) && selection.y < Number(updated.height)) {
                minimapSelection = {
                  mapId: updatedMapId,
                  x: selection.x,
                  y: selection.y,
                  events: (updated.events || []).filter(function (entry) { return Number(entry.x) === selection.x && Number(entry.y) === selection.y; })
                };
              } else minimapSelection = null;
            }
            mapId = updatedMapId; currentMapId = updatedMapId;
            renderMap(panel.querySelector(".tm-content"));
          } catch (_) {}
        }, 350);
      });
    }
    if (action === "minimap-cell-event-interpreter") {
      return showMinimapEventInterpreterPicker();
    }
    if (action === "minimap-pick-event") {
      var eventChooser = node.closest(".tm-pokemon-modal"); if (eventChooser) closeModal(eventChooser);
      return showMinimapEventInterpreterPicker(node.dataset.id);
    }
    if (action === "minimap-open-event-page") {
      var eventPagePicker = node.closest(".tm-pokemon-modal"); if (eventPagePicker) closeModal(eventPagePicker);
      return showEventInterpreterDialog(node.dataset.page, true);
    }
    if (action === "show-event-conditions") return showEventConditionsDialog(node.dataset.page);
    if (action === "show-event-interpreter") return showEventInterpreterDialog(node.dataset.page);
    if (action === "close-conditions" || action === "close-interpreter") return closeModal(node);
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
    if (action === "pokemon-progress") return run(function () {
      return mod.setPokemonProgress(node.dataset.kind, Number(node.dataset.index), node.checked);
    }, function () { toast(tr("进度已更新")); renderProgress(panel.querySelector(".tm-content")); });
    if (action === "show-empty-items") { showEmptyItems = node.checked; return loadBag(); }
    if (action === "toggle-hide-empty-system") { hideEmptySystemEntries = node.checked; return loadSystem(); }
    if (action === "lock-item") return run(function () { return mod.setItemLock("item", node.dataset.id, node.checked); }, function () { toast(tr("物品已锁定")); });
    if (action === "set-system-switch") {
      var switchValue = !!node.checked;
      var previousSwitchValue = node.dataset.currentValue === "1";
      var switchState = node.parentNode.querySelector(".tm-system-switch-state");
      try {
        mod.setSwitch(Number(node.dataset.id), switchValue);
        node.dataset.currentValue = switchValue ? "1" : "0";
        if (switchState) switchState.textContent = tr(switchValue ? "开" : "关");
      } catch (error) {
        node.checked = previousSwitchValue;
        if (switchState) switchState.textContent = tr(previousSwitchValue ? "开" : "关");
        console.error("Pokémon system switch update failed", error);
        toast(error.message || String(error), true);
      }
      return;
    }
    if (action === "set-system") {
      var rawValue = node.value;
      var parsedValue;
      try { parsedValue = JSON.parse(rawValue); } catch (_) { parsedValue = rawValue; }
      return run(function () { return mod.setVariable(Number(node.dataset.id), parsedValue); }, function () {
        toast(tr("变量已修改"));
        loadSystem();
      });
    }
    if (action === "lock-system") {
      var locked = !!node.checked;
      try {
        mod.setSystemLock(node.dataset.kind || systemKind, Number(node.dataset.id), locked);
        toast(tr(locked ? "系统值已锁定" : "已取消锁定"));
      } catch (error) {
        node.checked = !locked;
        console.error("Pokémon system lock update failed", error);
        toast(error.message || String(error), true);
      }
    }
  }
  function open() {
    if (panel || probe || rpgUI.isOpen()) return;
    if (detectedEngine !== "essentials") tryDetectRuntimeEngine();
    if (detectedEngine === "essentials") openPokemon();
    else if (detectedEngine === "unknown") openProbe();
    else rpgUI.open();
    scheduleEngineDetection(0);
  }
  function openProbe() {
    if (probe) return;
    probe = document.createElement("div"); probe.id = "tyranor-mod-root";
    probe.innerHTML = '<div class="tm-panel" role="dialog" aria-modal="true"><div class="tm-head"><strong>' + esc(tr("RGSS 游戏修改器")) +
      '</strong><button class="tm-close" data-action="close" aria-label="' + esc(tr("关闭")) + '">×</button></div><main class="tm-content"><div class="tm-note">' + esc(tr("正在识别当前游戏类型…")) + '</div></main></div>';
    document.documentElement.appendChild(probe);
    probe.addEventListener("click", function (event) { if (event.target.closest("[data-action='close']")) close(true); event.stopPropagation(); });
  }
  function scheduleEngineDetection(delay) {
    if (detectedEngine === "essentials" || engineDetectionAttempts >= MAX_ENGINE_DETECTION_ATTEMPTS || engineDetectionTimer) return;
    engineDetectionTimer = setTimeout(detect, delay);
  }
  function detect() {
    engineDetectionTimer = null;
    engineDetectionAttempts++;
    tryDetectRuntimeEngine();
    scheduleEngineDetection(8000);
  }
  function tryDetectRuntimeEngine() {
    try {
      if (mod.isReady()) {
        var loaded = mod.listCapabilities();
        if (loaded && loaded.engine === "essentials") applyRuntimeEngine(loaded);
      }
    } catch (error) { console.warn("RGSS cheat panel selection failed", error); }
  }
  function applyRuntimeEngine(loaded) {
    if (!loaded || loaded.engine !== "essentials") return;
    detectedEngine = "essentials";
    caps = loaded;
    setLauncherLabel("打开宝可梦修改器");
    if (probe) {
      probe.remove();
      probe = null;
      openPokemon();
      return;
    }
    if (rpgUI.isOpen() && typeof rpgUI.closeForEngineSwitch === "function") {
      rpgUI.closeForEngineSwitch();
      openPokemon();
    }
  }
  function setLauncherLabel(label) {
    var launcher = document.getElementById("tyranor-mod-launcher");
    if (launcher) launcher.setAttribute("aria-label", tr(label));
  }
  function close(restoreFocus) {
    if (probe) { probe.remove(); probe = null; notifyClosed(restoreFocus); return true; }
    if (panel) {
      if (detectTimer) clearTimeout(detectTimer); detectTimer = null;
      var openModals = panel.querySelectorAll(".tm-pokemon-modal");
      for (var modalIndex = openModals.length - 1; modalIndex >= 0; modalIndex--) closeModal(openModals[modalIndex]);
      panel.remove(); panel = null;
      notifyClosed(restoreFocus); return true;
    }
    return rpgUI.close(restoreFocus);
  }
  function notifyClosed(restoreFocus) {
    try { if (window.TyranorCheatNative) window.TyranorCheatNative.onPanelClosed(restoreFocus !== false); } catch (_) {}
  }
  function toggle() { if (panel || probe || rpgUI.isOpen()) close(); else open(); }
  function isOpen() { return !!(panel || probe || rpgUI.isOpen()); }
  window.TyranorModUI = {
    open: open,
    close: close,
    toggle: toggle,
    isOpen: isOpen,
    onRuntimeEngineDetected: applyRuntimeEngine,
  };
  if (detectedEngine !== "unknown") {
    setLauncherLabel(detectedEngine === "essentials" ? "打开宝可梦修改器" : "打开 RPG Maker 修改器");
  }
  scheduleEngineDetection(0);
})();
