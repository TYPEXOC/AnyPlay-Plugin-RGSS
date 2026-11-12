(function () {
  "use strict";
  if (window.TyranorModUI || !window.TyranorMod) return;
  var mod = window.TyranorMod;
  var root = null;
  var launcher = null;
  var dragState = null; // { startX, startY, startLeft, startTop, moved }
  var justDragged = false; // pointerup 设 true，click 消费后复位
  var userDragged = false; // 用户手动拖拽过，positionLauncher 不再覆盖
  var LAUNCHER_POS_KEY = "tyranor_mod_fab_pos";
  var activeTab = "quick";
  var context = { actorId: 0, actorEntries: null, itemKind: "item", systemKind: "switch", mapListExpanded: false, capabilities: null, capabilitiesLoading: false };
  var tabs = [
    ["quick", "快捷"], ["items", "物品"], ["actors", "角色"], ["system", "系统"],
    ["map", "地图"], ["battle", "战斗"], ["save", "存档"], ["keys", "键盘"], ["about", "说明"]
  ];
  var flagGroups = [
    ["角色与资源", [
      ["hpmp", "HP/MP不减"], ["tp", "TP不减"], ["points", "自定义点数不减"],
      ["gold", "金币不减"], ["coins", "游戏币不减"], ["status", "异常状态免疫"], ["items", "物品不减"]
    ]],
    ["探索", [
      ["noclip", "穿墙"], ["noEncounter", "无遇敌"], ["speed", "移动加速"],
      ["fastText", "快速文字"], ["eventSpeed", "事件加速"], ["msgSkip", "对话快进"]
    ]],
    ["战斗", [
      ["godMode", "无敌"], ["oneHit", "一击必杀"], ["alwaysCrit", "必定暴击"],
      ["escape", "必定逃跑成功"], ["freeSkill", "技能不消耗"]
    ]]
  ];
  function escapeHtml(value) { return String(value == null ? "" : value).replace(/[&<>'"]/g, function (c) { return ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;"})[c]; }); }
  function inputValue(id) { var node = root && root.querySelector("#" + id); return node ? node.value : ""; }
  function checked(id) { var node = root && root.querySelector("#" + id); return !!(node && node.checked); }
  function toast(message, error) {
    if (!root) return;
    var old = root.querySelector(".tm-toast"); if (old) old.remove();
    var node = document.createElement("div"); node.className = "tm-toast"; node.textContent = message || (error ? "操作失败" : "完成");
    if (error) node.style.background = "#8c1d18";
    root.appendChild(node); setTimeout(function () { if (node.parentNode) node.remove(); }, 2200);
  }
  function run(operation, success) {
    try {
      return Promise.resolve(operation()).then(function (value) { if (success) success(value); return value; }).catch(function (error) { console.error(error); toast(error.message || String(error), true); });
    } catch (error) { console.error(error); toast(error.message || String(error), true); return Promise.resolve(); }
  }
  function card(title, body, cls) { return '<section class="tm-card' + (cls ? " " + cls : "") + '"><h3>' + escapeHtml(title) + '</h3>' + body + '</section>'; }
  function row(label, control) { return '<div class="tm-row"><label>' + escapeHtml(label) + '</label>' + control + '</div>'; }
  function button(text, action, extra, data) { return '<button class="tm-btn ' + (extra || "") + '" data-action="' + action + '" ' + (data || "") + '>' + escapeHtml(text) + '</button>'; }
  function textInput(id, value, type, attrs) { return '<input class="tm-input" id="' + id + '" type="' + (type || "text") + '" value="' + escapeHtml(value == null ? "" : value) + '" ' + (attrs || "") + '>'; }
  function render() {
    if (!root) return;
    var content = root.querySelector(".tm-content");
    root.querySelectorAll(".tm-tab").forEach(function (node) { node.classList.toggle("active", node.dataset.tab === activeTab); });
    if (!mod.isReady()) { content.innerHTML = '<div class="tm-note">请先进入游戏存档或开始新游戏，修改器正在等待 RPG Maker 运行时就绪。</div>'; return; }
    if (activeTab === "quick") renderQuick(content);
    else if (activeTab === "items") renderItems(content);
    else if (activeTab === "actors") renderActors(content);
    else if (activeTab === "system") renderSystem(content);
    else if (activeTab === "map") renderMap(content);
    else if (activeTab === "battle") renderBattle(content);
    else if (activeTab === "save") renderSave(content);
    else if (activeTab === "keys") renderKeys(content);
    else renderAbout(content);
  }
  function renderQuick(content, refreshGold) {
    if (refreshGold === undefined) refreshGold = true;
    var state = mod.getState();
    var supported = context.capabilities && context.capabilities.toggles;
    var cards = flagGroups.map(function (group) {
      var entries = group[1].filter(function (entry) {
        var capability = entry[0] === "godMode" ? "invincible" : entry[0] === "alwaysCrit" ? "crit" : entry[0];
        return !supported || supported.indexOf(capability) >= 0;
      });
      if (!entries.length) return "";
      var toggles = entries.map(function (entry) {
        return row(entry[1], '<input class="tm-switch" type="checkbox" data-action="flag" data-flag="' + entry[0] + '" ' + (state[entry[0]] ? "checked" : "") + '>');
      }).join("");
      if (group[0] === "探索" && (!supported || supported.indexOf("speed") >= 0)) {
        toggles += row("移动速度倍数", '<div class="tm-row"><input class="tm-input" id="tm-speed" type="number" min="0.25" max="8" step="0.25" value="' + escapeHtml(state.speedMultiplier || 2) + '">' + button("设置", "set-speed", "primary") + '</div>');
      }
      return card(group[0], toggles);
    }).join("");
    var cachedGold = context.gold == null ? "…" : context.gold;
    var gold = row("当前金币", '<span class="tm-badge" id="tm-gold-current">' + cachedGold + '</span>') + '<div class="tm-row">' + textInput("tm-gold", cachedGold, "number", 'min="0"') + button("设置", "set-gold", "primary") + button("拉满", "max-gold") + '</div>';
    var party = '<div class="tm-row">' + button("全体恢复", "recover-party", "primary") + button("刷新页面", "render") + '</div>';
    content.innerHTML = '<div class="tm-grid">' + cards + card("金币", gold) + card("队伍", party) + '</div>';
    // Render the panel before crossing the Ruby command-file bridge. One query
    // used to block open() twice (the badge and input each called getGold).
    if (refreshGold) setTimeout(function () {
        if (!root || activeTab !== "quick") return;
        try {
          var value = mod.getGold();
          context.gold = value;
          var current = root.querySelector("#tm-gold-current");
          var input = root.querySelector("#tm-gold");
          if (current) current.textContent = String(value);
          if (input && document.activeElement !== input) input.value = String(value);
        } catch (error) {
          console.warn("RGSS gold refresh failed", error);
        }
      }, 0);
    if (!context.capabilities && !context.capabilitiesLoading) {
      context.capabilitiesLoading = true;
      setTimeout(function () {
        try {
          var loaded = mod.listCapabilities();
          if (loaded && loaded.engine !== "unknown" && Array.isArray(loaded.toggles)) context.capabilities = loaded;
        } catch (error) {
          console.warn("RGSS cheat capability read failed", error);
        }
        context.capabilitiesLoading = false;
        if (context.capabilities && root && activeTab === "quick") renderQuick(root.querySelector(".tm-content"), false);
      }, 0);
    }
  }
  function renderItems(content) {
    var kinds = '<select class="tm-select" id="tm-item-kind" data-action="item-kind"><option value="item">物品</option><option value="weapon">武器</option><option value="armor">护甲</option></select>';
    var tools = '<div class="tm-row">' + kinds + textInput("tm-item-query", "", "search", 'placeholder="搜索名称"') + button("搜索", "render-items", "primary") + '</div>';
    content.innerHTML = card("物品数据库", tools + '<div id="tm-item-list" class="tm-list"><div class="tm-note">加载中…</div></div>', "tm-fill-card");
    root.querySelector("#tm-item-kind").value = context.itemKind;
    loadItems();
  }
  function loadItems() {
    context.itemKind = inputValue("tm-item-kind") || context.itemKind;
    var result = mod.listDatabase(context.itemKind, inputValue("tm-item-query"), 0, 200);
    var list = root.querySelector("#tm-item-list");
    list.innerHTML = result.values.length ? result.values.map(function (item) {
      return '<div class="tm-item"><span class="tm-name" title="' + escapeHtml(item.description) + '">#' + item.id + ' ' + escapeHtml(item.name) + '</span>' +
        textInput("tm-count-" + item.id, item.count, "number", 'min="0" style="width:72px"') + button("应用", "set-item", "", 'data-id="' + item.id + '"') + '</div>';
    }).join("") : '<div class="tm-note">没有匹配项目</div>';
  }
  function actorSelect(actors) {
    return '<select class="tm-select" id="tm-actor" data-action="actor-select">' + actors.map(function (actor) { return '<option value="' + actor.id + '">' + escapeHtml(actor.name) + '</option>'; }).join("") + '</select>';
  }
  function renderActors(content) {
    var actors = mod.listActors();
    if (!actors.length) { content.innerHTML = '<div class="tm-note">当前队伍没有角色</div>'; return; }
    context.actorEntries = actors;
    if (!context.actorId || !actors.some(function (a) { return a.id === context.actorId; })) context.actorId = actors[0].id;
    content.innerHTML = '<div class="tm-row">' + actorSelect(actors) + button("恢复", "recover-actor", "primary") + '</div><div id="tm-actor-body"></div>';
    root.querySelector("#tm-actor").value = String(context.actorId); renderActorBody(actors);
  }
  function renderActorBody(actors) {
    actors = actors || mod.listActors();
    context.actorEntries = actors;
    var actor = actors.filter(function (a) { return a.id === context.actorId; })[0];
    if (!actor) return;
    var stats = ["level","hp","mp","mhp","mmp","atk","def","mat","mdf","agi","luk"].map(function (key) {
      return '<div class="tm-item"><span class="tm-name">' + key.toUpperCase() + '</span>' + textInput("tm-stat-" + key, actor[key], "number", 'style="width:94px"') + button("设置", "set-stat", "", 'data-key="' + key + '"') + '</div>';
    }).join("");
    var skillIds = mod.actorSkills(actor.id).map(function (s) { return '#' + s.id + ' ' + escapeHtml(s.name); }).join('、') || '无';
    var stateIds = mod.actorStates(actor.id).map(function (s) { return '#' + s.id + ' ' + escapeHtml(s.name); }).join('、') || '无';
    var equips = mod.actorEquips(actor.id).map(function (e) { return '槽位' + e.slot + '：' + escapeHtml(e.name); }).join('<br>') || '无';
    var skillControls = '<div class="tm-row">' + textInput("tm-skill-id", "", "number", 'placeholder="技能 ID"') + button("学习", "learn-skill", "primary") + button("遗忘", "forget-skill") + '</div><small>' + skillIds + '</small>';
    var stateControls = '<div class="tm-row">' + textInput("tm-state-id", "", "number", 'placeholder="状态 ID"') + button("附加", "add-state", "primary") + button("移除", "remove-state") + '</div><small>' + stateIds + '</small>';
    var equipControls = '<div class="tm-row">' + textInput("tm-equip-slot", "0", "number", 'placeholder="槽位"') + '<select id="tm-equip-kind" class="tm-select"><option value="weapon">武器</option><option value="armor">护甲</option></select>' + textInput("tm-equip-id", "0", "number", 'placeholder="装备 ID，0=卸下"') + button("更换", "change-equip", "primary") + '</div><small>' + equips + '</small>';
    root.querySelector("#tm-actor-body").innerHTML = '<div class="tm-grid">' + card("属性", '<div class="tm-list">' + stats + '</div>') + card("技能", skillControls) + card("状态", stateControls) + card("装备", equipControls) + '</div>';
  }
  function renderSystem(content) {
    var kind = '<select class="tm-select" id="tm-system-kind" data-action="system-kind"><option value="switch">开关</option><option value="variable">变量</option></select>';
    content.innerHTML = card("开关与变量", '<div class="tm-row">' + kind + textInput("tm-system-query", "", "search", 'placeholder="名称或 ID"') + button("搜索", "render-system", "primary") + '</div><div id="tm-system-list" class="tm-list"></div>', "tm-fill-card");
    root.querySelector("#tm-system-kind").value = context.systemKind; loadSystem();
  }
  function loadSystem() {
    context.systemKind = inputValue("tm-system-kind") || context.systemKind;
    var result = context.systemKind === "switch" ? mod.listSwitches(inputValue("tm-system-query"), 0, 200) : mod.listVariables(inputValue("tm-system-query"), 0, 200);
    root.querySelector("#tm-system-list").innerHTML = result.values.map(function (item) {
      var control = context.systemKind === "switch" ? '<input class="tm-switch" id="tm-system-' + item.id + '" type="checkbox" ' + (item.value ? "checked" : "") + '>' : textInput("tm-system-" + item.id, typeof item.value === "object" ? JSON.stringify(item.value) : item.value, "text");
      return '<div class="tm-item"><span class="tm-name">#' + item.id + ' ' + escapeHtml(item.name) + '</span>' + control + button("设置", "set-system", "", 'data-id="' + item.id + '"') + '</div>';
    }).join("") || '<div class="tm-note">没有匹配项目</div>';
  }
  function renderMap(content) {
    var pos = mod.currentPosition();
    context.mapListExpanded = false;
    var currentMapLabel = "#" + ("0000" + (pos && pos.mapId || 0)).slice(-4);
    try {
      var currentMapId = Number(pos && pos.mapId);
      var currentMaps = mod.listMaps(String(currentMapId), 0, 300).values || [];
      var currentMap = currentMaps.filter(function (map) { return Number(map.id) === currentMapId; })[0];
      if (currentMap && currentMap.name) currentMapLabel += ": " + currentMap.name;
    } catch (_) {}
    var mapPicker = '<button class="tm-btn tm-map-picker" data-action="toggle-map-list" title="' + escapeHtml(currentMapLabel) + '"><span class="tm-map-picker-label">' + escapeHtml(currentMapLabel) + '</span><span class="tm-map-picker-arrow" aria-hidden="true">▾</span></button>';
    var browseRow = '<div class="tm-map-picker-row">' + mapPicker + '</div><div class="tm-row">' + textInput("tm-browse-query", "", "search", 'placeholder="搜索"') + button("搜索", "render-browse", "primary") + '</div>';
    var teleportCard = card("传送", '<div class="tm-row">' + textInput("tm-map-id", pos && pos.mapId, "number", 'placeholder="地图 ID"') + textInput("tm-map-x", pos && pos.x, "number", 'placeholder="X"') + textInput("tm-map-y", pos && pos.y, "number", 'placeholder="Y"') + button("传送", "teleport", "primary") + '</div>');
    var eventCard = card("事件操作", '<div class="tm-card-body" id="tm-event-panel">' + eventPanelHtml() + '</div>');
    content.innerHTML = '<div class="tm-grid tm-map-grid">'
      + '<section class="tm-card tm-browse-card"><h3>浏览</h3>' + browseRow + '<div class="tm-list" id="tm-browse-list"></div><div class="tm-list tm-map-dropdown" id="tm-map-list" style="display:none"></div></section>'
      + teleportCard + eventCard + '</div>';
    context.eventAll = [];
    try { context.eventAll = mod.listEvents().values; } catch (_) { context.eventAll = []; }
    loadBrowse();
  }
  function fillTeleport(mapId, x, y) {
    var mi = root.querySelector("#tm-map-id"), xi = root.querySelector("#tm-map-x"), yi = root.querySelector("#tm-map-y");
    if (mi && mapId != null) mi.value = mapId;
    if (xi && x != null) xi.value = x;
    if (yi && y != null) yi.value = y;
  }
  function loadBrowse() {
    var queryInput = root.querySelector("#tm-browse-query");
    if (queryInput) queryInput.placeholder = context.mapListExpanded ? "搜索地图名称 / ID" : "搜索 ID / 名称";
    var mapPicker = root.querySelector(".tm-map-picker");
    if (mapPicker) {
      mapPicker.classList.toggle("primary", context.mapListExpanded);
      var arrow = mapPicker.querySelector(".tm-map-picker-arrow");
      if (arrow) arrow.textContent = context.mapListExpanded ? "▴" : "▾";
    }
    var mapList = root.querySelector("#tm-map-list");
    if (context.mapListExpanded) {
      renderMapList();
      if (mapList) {
        var eventList = root.querySelector("#tm-browse-list");
        mapList.style.top = (eventList ? eventList.offsetTop : 124) + "px";
        mapList.style.display = "flex";
      }
    } else {
      if (mapList) mapList.style.display = "none";
      renderEventList();
    }
  }
  function renderMapList() {
    var list = root.querySelector("#tm-map-list");
    if (!list) return;
    var pos = null;
    try { pos = mod.currentPosition(); } catch (_) { pos = null; }
    var maps = [];
    try { maps = mod.listMaps(inputValue("tm-browse-query"), 0, 300).values; } catch (e) {
      list.innerHTML = '<div class="tm-note">' + escapeHtml(String(e && e.message || e)) + '</div>'; return;
    }
    function mapDepth(map) { return Math.max(0, Number(map.depth) || 0); }
    function hasLaterSibling(index, depth) {
      for (var i = index + 1; i < maps.length; i++) {
        var nextDepth = mapDepth(maps[i]);
        if (nextDepth < depth) return false;
        if (nextDepth === depth) return true;
      }
      return false;
    }
    function treePrefix(index) {
      var depth = mapDepth(maps[index]);
      if (!depth) return "";
      var prefix = "";
      for (var level = 0; level < depth; level++) {
        var hasLaterAtLevel = false;
        for (var i = index + 1; i < maps.length; i++) {
          var nextDepth = mapDepth(maps[i]);
          if (nextDepth <= level) {
            hasLaterAtLevel = nextDepth === level;
            break;
          }
        }
        prefix += hasLaterAtLevel ? "│  " : "   ";
      }
      return prefix + (hasLaterSibling(index, depth) ? "├─ " : "└─ ");
    }
    list.innerHTML = maps.map(function (m, index) {
      var here = pos && Number(pos.mapId) === Number(m.id);
      var picked = Number(context.pickedMapId) === Number(m.id);
      var paddedId = ("0000" + m.id).slice(-4);
      var name = '<span class="tm-map-tree-prefix">' + treePrefix(index) + '</span><span class="tm-map-tree-label">#' + paddedId + ": " + escapeHtml(m.name) + '</span>';
      return '<div class="tm-item' + (picked ? " tm-active" : "") + '"><span class="tm-name tm-map-name">' + name + '</span>' + (here ? '<span class="tm-badge tm-on">你在这里</span>' : '') + button("选择", "pick-map", "", 'data-id="' + m.id + '"') + '</div>';
    }).join("") || '<div class="tm-note">没有匹配地图</div>';
    var target = list.querySelector(".tm-item .tm-badge.tm-on");
    var active = target ? target.closest(".tm-item") : list.querySelector(".tm-item.tm-active");
    if (active && active.scrollIntoView) { try { active.scrollIntoView({ block: "center" }); } catch (_) { try { active.scrollIntoView(); } catch (_) {} } }
  }
  function renderEventList() {
    var list = root.querySelector("#tm-browse-list");
    if (!list) return;
    list.innerHTML = filteredEvents().map(function (ev) {
      return '<div class="tm-item' + (String(context.eventId) === String(ev.id) ? " tm-active" : "") + '"><span class="tm-name">#' + ev.id + ' ' + escapeHtml(ev.name) + '</span><span class="tm-badge">' + ev.x + ',' + ev.y + '</span>' + button("选择", "pick-event", "", 'data-id="' + ev.id + '"') + '</div>';
    }).join("") || '<div class="tm-note">当前地图没有事件（进入地图场景后打开）</div>';
  }
  function filteredEvents() {
    var query = String(inputValue("tm-browse-query") || "").toLowerCase();
    if (!query) return context.eventAll || [];
    return (context.eventAll || []).filter(function (ev) {
      return String(ev.name).toLowerCase().indexOf(query) >= 0 || String(ev.id).indexOf(query) >= 0;
    });
  }
  function eventPanelHtml() {
    if (!context.eventId) return '<div class="tm-note">在左侧选择事件查看详情</div>';
    var detail;
    try { detail = mod.eventDetail(context.eventId); } catch (e) { return '<div class="tm-note">' + escapeHtml(String(e && e.message || e)) + '</div>'; }
    var selfSwitches = ["A", "B", "C", "D"].map(function (k) {
      return '<span class="tm-badge' + (detail.selfSwitches[k] ? " tm-on" : "") + '">' + k + (detail.selfSwitches[k] ? ":开" : ":关") + '</span>';
    }).join(" ");
    var allOn = ["A", "B", "C", "D"].every(function (k) { return detail.selfSwitches[k]; });
    var state = mod.getState();
    return '<div class="tm-row">' + button(state.noclip ? "穿墙:开" : "穿墙:关", "flag", state.noclip ? "primary" : "", 'data-flag="noclip"') + button("迷你地图", "show-minimap", "", 'data-id="' + detail.id + '"') + '</div>'
      + '<div class="tm-row"><small>Pos:</small><span class="tm-badge">' + detail.x + ',' + detail.y + '</span><small>名称:</small><span class="tm-name">' + escapeHtml(detail.name) + '</span></div>'
      + '<div class="tm-row"><small>独立开关:</small>' + selfSwitches + button(allOn ? "全开" : "全关", "selfswitch-toggle", allOn ? "primary" : "danger", 'data-id="' + detail.id + '"') + '</div>';
  }
  function renderEventPanel() {
    var panel = root.querySelector("#tm-event-panel");
    if (panel) panel.innerHTML = eventPanelHtml();
  }
  function showMinimapDialog() {
    var data;
    try { data = mod.minimapData(); } catch (e) { toast(String(e && e.message || e)); return; }
    var lg = '<span class="tm-lg"><span style="display:inline-block;width:12px;height:12px;border-radius:3px;background:#3a4640"></span>无法通行</span>'
      + '<span class="tm-lg"><span style="display:inline-block;width:12px;height:12px;border-radius:3px;background:#cfe8d6"></span>可通行</span>'
      + '<span class="tm-lg"><span style="display:inline-block;width:12px;height:12px;border-radius:3px;background:#fff;border:1px solid #cfd6d2"></span>事件</span>'
      + '<span class="tm-lg"><span style="display:inline-block;width:12px;height:12px;border-radius:3px;background:#69f0ae"></span>你</span>';
    var modal = document.createElement("div");
    modal.className = "tm-modal";
    modal.innerHTML = '<div class="tm-modal-box">'
      + '<div class="tm-modal-head"><strong>迷你地图 #' + data.mapId + '</strong>' + lg + button("×", "close-minimap", "") + '</div>'
      + '<div class="tm-modal-body"><canvas class="tm-minimap" id="tm-minimap-canvas"></canvas></div>'
      + '<div class="tm-modal-hint">点击事件块选中该事件；点外部区域或 × 关闭</div>'
      + '</div>';
    modal.addEventListener("click", function (e) { if (e.target === modal) { modal.remove(); renderEventPanel(); } });
    root.appendChild(modal);
    var canvas = modal.querySelector("#tm-minimap-canvas");
    var cssW = canvas.parentElement.clientWidth || 480;
    var tile = Math.max(3, Math.floor(cssW / data.width));
    var cssH = tile * data.height;
    var dpr = window.devicePixelRatio || 1;
    canvas.style.width = cssW + "px";
    canvas.style.height = cssH + "px";
    canvas.width = Math.round(cssW * dpr);
    canvas.height = Math.round(cssH * dpr);
    var ctx = canvas.getContext("2d");
    ctx.scale(dpr, dpr);
    for (var y = 0; y < data.height; y++) {
      for (var x = 0; x < data.width; x++) {
        ctx.fillStyle = data.rows[y].charAt(x) === "1" ? "#cfe8d6" : "#3a4640";
        ctx.fillRect(x * tile, y * tile, tile, tile);
      }
    }
    data.events.forEach(function (ev) {
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(ev.x * tile, ev.y * tile, tile, tile);
      if (tile >= 6) {
        ctx.fillStyle = "#202522";
        ctx.font = Math.floor(tile * 0.72) + "px sans-serif";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(String(ev.id), ev.x * tile + tile / 2, ev.y * tile + tile / 2 + 1);
      }
    });
    if (data.player) {
      ctx.strokeStyle = "#22c55e";
      ctx.lineWidth = Math.max(1.5, tile / 4);
      ctx.strokeRect(data.player.x * tile + 1, data.player.y * tile + 1, tile - 2, tile - 2);
    }
    canvas.addEventListener("click", function (e) {
      var rect = canvas.getBoundingClientRect();
      var tx = Math.floor((e.clientX - rect.left) / tile);
      var ty = Math.floor((e.clientY - rect.top) / tile);
      var hit = data.events.filter(function (ev) { return ev.x === tx && ev.y === ty; })[0];
      if (!hit) return;
      context.eventId = hit.id;
      modal.remove();
      renderEventList();
      renderEventPanel();
      toast("已选中事件 #" + hit.id);
    });
  }
  function renderBattle(content) {
    var snapshot = mod.battleSnapshot();
    if (!snapshot.inBattle) { content.innerHTML = '<div class="tm-note">当前不在战斗中</div>'; return; }
    var enemies = snapshot.enemies.map(function (enemy) { return '<div class="tm-item"><span class="tm-name">' + escapeHtml(enemy.name) + ' HP ' + enemy.hp + '/' + enemy.mhp + '</span>' + textInput("tm-enemy-" + enemy.index, enemy.hp, "number", 'style="width:86px"') + button("设置", "set-enemy", "", 'data-index="' + enemy.index + '"') + '</div>'; }).join("");
    var actors = snapshot.actors.map(function (actor) { return '<div class="tm-item"><span class="tm-name">' + escapeHtml(actor.name) + '</span><span class="tm-badge">HP ' + actor.hp + '/' + actor.mhp + '</span></div>'; }).join("");
    var actions = '<div class="tm-row">' + button("消灭全部敌人", "kill-enemies", "danger") + button("强制胜利", "victory", "primary") + button("强制逃跑", "escape") + '</div>';
    content.innerHTML = '<div class="tm-grid">' + card("战斗操作", actions) + card("敌方", '<div class="tm-list">' + enemies + '</div>') + card("我方", '<div class="tm-list">' + actors + '</div>') + '</div>';
  }
  function renderSave(content) {
    content.innerHTML = card("存档管理", '<div id="tm-save-list" class="tm-list"><div class="tm-note">读取中…</div></div>');
    run(function () { return mod.listSaveSlots(); }, function (slots) {
      root.querySelector("#tm-save-list").innerHTML = slots.map(function (slot) {
        return '<div class="tm-item"><span class="tm-name">存档 ' + slot.id + (slot.exists ? ' · ' + escapeHtml(slot.playtime || slot.title || '已有数据') : ' · 空') + '</span>' + button("保存", "save", "primary", 'data-id="' + slot.id + '"') + (slot.exists ? button("读取", "load", "", 'data-id="' + slot.id + '"') + button("删除", "delete-save", "danger", 'data-id="' + slot.id + '"') : '') + '</div>';
      }).join("");
    });
  }
  function renderAbout(content) {
    content.innerHTML = card("Tyranor 修改器", '<p>版本 ' + escapeHtml(mod.version) + '</p><p>修改器只操作当前 RGSS 游戏的运行时对象。部分深度定制脚本可能覆盖标准引擎 API；遇到异常时可在单游戏设置中关闭修改器。</p><p>事件加速和对话快进可能跳过演出或等待，建议先保存游戏。</p>');
  }
  function inputBridge() {
    return window.TyranorInputNative || null;
  }
  function renderKeys(content) {
    var bridge = inputBridge();
    var hasPad = false;
    try { hasPad = !!(bridge && bridge.isAvailable()); } catch (e) { hasPad = false; }
    content.innerHTML = card("虚拟按键", hasPad
      ? '<p>按键层由应用原生实现，可自定义按键的位置、文字、大小与输出键位。进入编辑后：</p><ul style="margin:6px 0 10px;padding-left:18px;font-size:12px"><li>点按按键选中，拖拽调整位置</li><li>右侧面板改文字 / 尺寸 / 键位</li><li>可复制、删除按键，或恢复默认</li><li>保存后立即生效</li></ul><div class="tm-row">' + button("进入按键编辑", "enter-keys", "primary") + button("恢复默认", "reset-keys") + '</div>'
      : '<p>当前未启用虚拟按键。可在应用的引擎设置中开启。</p>');
  }
  function handleAction(action, node) {
    if (action === "close") return close();
    if (action === "render") return render();
    if (action === "flag") {
      var flagOn = node.type === "checkbox" ? node.checked : !mod.getState()[node.dataset.flag];
      return run(function () { return mod.setFlag(node.dataset.flag, flagOn); }, function () { toast("设置已保存"); renderEventPanel(); })
        .then(function (result) { if (result === undefined && node.type === "checkbox") node.checked = !flagOn; });
    }
    if (action === "set-speed") return run(function () { return mod.setSpeed(inputValue("tm-speed")); }, function () { toast("移动速度已设置"); renderQuick(root.querySelector(".tm-content"), false); });
    if (action === "set-gold") return run(function () { mod.setGold(inputValue("tm-gold")); }, function () { toast("金币已修改"); render(); });
    if (action === "max-gold") return run(function () { mod.setGold(99999999); }, function () { toast("金币已拉满"); render(); });
    if (action === "recover-party") return run(mod.recoverParty, function () { toast("队伍已恢复"); render(); });
    if (action === "item-kind" || action === "render-items") return loadItems();
    if (action === "set-item") return run(function () { mod.setItemCount(context.itemKind, node.dataset.id, inputValue("tm-count-" + node.dataset.id)); }, function () { toast("数量已修改"); loadItems(); });
    if (action === "actor-select") { context.actorId = Number(node.value); return renderActorBody(context.actorEntries || []); }
    if (action === "recover-actor") return run(function () { mod.recoverActor(context.actorId); }, function () { toast("角色已恢复"); renderActorBody(); });
    if (action === "set-stat") return run(function () { mod.setActorStat(context.actorId, node.dataset.key, inputValue("tm-stat-" + node.dataset.key)); }, function () { toast("属性已修改"); renderActorBody(); });
    if (action === "learn-skill" || action === "forget-skill") return run(function () { return action === "learn-skill" ? mod.learnSkill(context.actorId, inputValue("tm-skill-id")) : mod.forgetSkill(context.actorId, inputValue("tm-skill-id")); }, function () { renderActorBody(); });
    if (action === "add-state" || action === "remove-state") return run(function () { return action === "add-state" ? mod.addState(context.actorId, inputValue("tm-state-id")) : mod.removeState(context.actorId, inputValue("tm-state-id")); }, function () { renderActorBody(); });
    if (action === "change-equip") return run(function () { mod.changeEquip(context.actorId, inputValue("tm-equip-slot"), inputValue("tm-equip-kind"), inputValue("tm-equip-id")); }, function () { toast("装备已更换"); renderActorBody(); });
    if (action === "system-kind" || action === "render-system") return loadSystem();
    if (action === "set-system") return run(function () { var id = node.dataset.id; if (context.systemKind === "switch") mod.setSwitch(id, checked("tm-system-" + id)); else { var raw = inputValue("tm-system-" + id); var value; try { value = JSON.parse(raw); } catch (_) { value = raw; } mod.setVariable(id, value); } }, function () { toast("系统值已修改"); loadSystem(); });
    if (action === "toggle-map-list") {
      context.mapListExpanded = !context.mapListExpanded;
      var browseQuery = root.querySelector("#tm-browse-query");
      if (browseQuery) browseQuery.value = "";
      return loadBrowse();
    }
    if (action === "render-browse") return loadBrowse();
    if (action === "pick-map") { fillTeleport(node.dataset.id, null, null); context.pickedMapId = Number(node.dataset.id); if (context.mapListExpanded) renderMapList(); return; }
    if (action === "pick-event") {
      context.eventId = Number(node.dataset.id);
      var ev = (context.eventAll || []).filter(function (e2) { return String(e2.id) === node.dataset.id; })[0];
      if (ev) fillTeleport(ev.mapId, ev.x, ev.y);
      renderEventList(); renderEventPanel(); return;
    }
    if (action === "selfswitch-toggle") {
      var sid = node.dataset.id;
      return run(function () { var cur = mod.eventDetail(sid).selfSwitches; var on = !["A", "B", "C", "D"].every(function (k) { return cur[k]; }); mod.setEventSelfSwitches(sid, on); return on; }, function (on) { toast(on ? "独立开关 A–D 已全开" : "独立开关 A–D 已全关"); renderEventPanel(); });
    }
    if (action === "show-minimap") return showMinimapDialog();
    if (action === "close-minimap") { var modal = root.querySelector(".tm-modal"); if (modal) modal.remove(); return; }
    if (action === "teleport") return run(function () { return mod.teleport(inputValue("tm-map-id"), inputValue("tm-map-x"), inputValue("tm-map-y"), 2, 0); }, function () { toast("已预约传送"); close(); });
    if (action === "set-enemy") return run(function () { mod.setEnemyHp(node.dataset.index, inputValue("tm-enemy-" + node.dataset.index)); }, render);
    if (action === "kill-enemies") return run(mod.killAllEnemies, render);
    if (action === "victory") return run(mod.forceVictory, close);
    if (action === "escape") return run(mod.forceEscape, close);
    if (action === "save") return run(function () { return mod.saveGame(node.dataset.id); }, function () { toast("保存完成"); renderSave(root.querySelector(".tm-content")); });
    if (action === "load") return run(function () { return mod.loadGame(node.dataset.id); }, function () { toast("读取完成"); close(); });
    if (action === "delete-save") return run(function () { return mod.deleteSave(node.dataset.id); }, function () { toast("存档已删除"); renderSave(root.querySelector(".tm-content")); });
    if (action === "enter-keys") {
      var bridge = inputBridge();
      if (!bridge) { toast("当前未启用虚拟按键", true); return; }
      var available = true;
      try { available = !!bridge.isAvailable(); } catch (e) { available = false; }
      if (!available) { toast("当前未启用虚拟按键", true); return; }
      try { bridge.enterEdit(); } catch (e) { toast(String(e && e.message || e), true); return; }
      close(false);
      return;
    }
    if (action === "reset-keys") {
      var rbridge = inputBridge();
      if (!rbridge) { toast("当前未启用虚拟按键", true); return; }
      try {
        rbridge.resetToDefaults();
        toast("已恢复默认");
      } catch (e) { toast(String(e && e.message || e), true); }
      return;
    }
  }
  function block(event) {
    if (!root) return;
    if (event.target && (event.target.closest && event.target.closest("#tyranor-mod-root"))) event.stopPropagation();
  }
  function open() {
    if (root) return;
    root = document.createElement("div"); root.id = "tyranor-mod-root";
    root.innerHTML = '<div class="tm-panel" role="dialog" aria-modal="true"><div class="tm-head"><strong>RPG Maker 修改器 · RGSS</strong><button class="tm-close" data-action="close" aria-label="关闭">×</button></div><div class="tm-tabs">' + tabs.map(function (tab) { return '<button class="tm-tab" data-tab="' + tab[0] + '">' + tab[1] + '</button>'; }).join("") + '</div><main class="tm-content"></main></div>';
    document.documentElement.appendChild(root);
    root.addEventListener("click", function (event) {
      event.stopPropagation();
      var tab = event.target.closest("[data-tab]"); if (tab) { activeTab = tab.dataset.tab; render(); return; }
      var action = event.target.closest("[data-action]"); if (action) handleAction(action.dataset.action, action);
    });
    root.addEventListener("change", function (event) { var action = event.target.closest("[data-action]"); if (action) handleAction(action.dataset.action, action); event.stopPropagation(); });
    ["touchstart","touchmove","touchend","pointerdown","pointerup","mousedown","mouseup","keydown","keyup","wheel"].forEach(function (name) { root.addEventListener(name, block); });
    render();
  }
  function close(restoreGameFocus) {
    if (!root) return false;
    root.remove(); root = null;
    try { if (window.TyranorCheatNative) window.TyranorCheatNative.onPanelClosed(restoreGameFocus !== false); } catch (_) {}
    return true;
  }
  function toggle() { if (root) close(); else open(); }
  function isOpen() { return !!root; }
  function installLauncher() {
    if (launcher || !document.body) return;
    launcher = document.createElement("button"); launcher.id = "tyranor-mod-launcher"; launcher.innerHTML = '<span class="tm-launcher-icon" aria-hidden="true"></span>'; launcher.setAttribute("aria-label", "打开 RPG Maker 修改器");
    // 点击：拖拽后不触发（pointerup → click 时序问题）
    launcher.addEventListener("click", function (event) {
      event.preventDefault(); event.stopPropagation();
      if (justDragged) { justDragged = false; return; }
      toggle();
    });
    // 阻止 touch/pointer 冒泡到 WebView
    ["touchstart","touchend","pointerdown","pointerup"].forEach(function (name) { launcher.addEventListener(name, function (event) { event.stopPropagation(); }); });
    // 拖拽支持：pointerdown 开始追踪，移动超过 8px 视为拖拽
    launcher.addEventListener("pointerdown", function (event) {
      event.preventDefault();
      var rect = launcher.getBoundingClientRect();
      dragState = { startX: event.clientX, startY: event.clientY, startLeft: rect.left, startTop: rect.top, moved: false };
      launcher.setPointerCapture(event.pointerId);
    });
    launcher.addEventListener("pointermove", function (event) {
      if (!dragState) return;
      var dx = event.clientX - dragState.startX;
      var dy = event.clientY - dragState.startY;
      if (!dragState.moved && Math.abs(dx) + Math.abs(dy) < 8) return;
      dragState.moved = true;
      launcher.style.left = Math.max(0, Math.min(window.innerWidth - launcher.offsetWidth, dragState.startLeft + dx)) + "px";
      launcher.style.top = Math.max(0, Math.min(window.innerHeight - launcher.offsetHeight, dragState.startTop + dy)) + "px";
      launcher.style.right = "auto";
    });
    var endDrag = function (event) {
      if (dragState && dragState.moved) {
        justDragged = true;
        userDragged = true;
        setTimeout(function () { justDragged = false; }, 100);
        launcher.style.left = launcher.offsetLeft + "px";
        launcher.style.top = launcher.offsetTop + "px";
        try { localStorage.setItem(LAUNCHER_POS_KEY, launcher.offsetLeft + "," + launcher.offsetTop); } catch (e) {}
      }
      dragState = null;
    };
    launcher.addEventListener("pointerup", endDrag);
    launcher.addEventListener("pointercancel", function () { dragState = null; });
    document.body.appendChild(launcher);
    // 恢复上次拖拽位置
    try {
      var saved = localStorage.getItem(LAUNCHER_POS_KEY);
      if (saved) {
        var parts = saved.split(",");
        var sx = parseInt(parts[0], 10), sy = parseInt(parts[1], 10);
        if (sx >= 0 && sy >= 0 && sx < window.innerWidth && sy < window.innerHeight) {
          launcher.style.left = sx + "px";
          launcher.style.top = sy + "px";
          launcher.style.right = "auto";
          userDragged = true;
        }
      }
    } catch (e) {}
    positionLauncher();
    window.addEventListener("resize", positionLauncher);
  }
  // 虚拟按键已改为原生实现（左侧 FAB + 覆盖层），不再发布 __touchPadMetrics；
  // 悬浮球默认停靠右侧，用户可拖拽到任意位置并持久化。
  function positionLauncher() {
    if (!launcher || userDragged) return;
    launcher.style.left = "";
    launcher.style.right = "10px";
  }
  window.TyranorModUI = { open: open, close: close, toggle: toggle, isOpen: isOpen };
})();
