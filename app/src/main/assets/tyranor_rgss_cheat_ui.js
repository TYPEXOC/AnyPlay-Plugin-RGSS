(function () {
  "use strict";
  if (window.TyranorModUI || !window.TyranorMod) return;
  var mod = window.TyranorMod;
  function translated(value) {
    return window.TyranorCheatTranslate ? window.TyranorCheatTranslate(value) : value;
  }
  var root = null;
  var launcher = null;
  var dragState = null; // { startX, startY, startLeft, startTop, moved }
  var justDragged = false; // pointerup 设 true，click 消费后复位
  var userDragged = false; // 用户手动拖拽过，positionLauncher 不再覆盖
  var LAUNCHER_POS_KEY = "tyranor_mod_fab_pos";
  var activeTab = "quick";
  var goldCommitPending = null;
  var pendingEquipScroll = null;
  var context = { actorId: 0, actorEntries: null, actorSection: "stats", actorQuery: "", actorSkillEntries: [], actorStateEntries: [], skillQuery: "", stateQuery: "", hideEmptySkills: false, hideEmptySystemEntries: false, hideEmptyEquipCandidates: false, hideEmptyBattleEnemies: false, hideEmptyBattleActors: false, equipSlot: null, equipKind: "", quickSection: "角色与资源", inBattle: false, itemKind: "item", itemQuery: "", itemKindExpanded: false, actorClassExpanded: false, itemEntries: [], selectedItemId: null, systemKind: "switch", systemQuery: "", systemKindExpanded: false, mapListExpanded: false, currentMapId: 0, selectedMapId: 0, eventId: 0, selectedEventDetail: null, mapEntries: [], capabilities: null, capabilitiesLoading: false };
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
      ["noclip", "穿墙"], ["noEncounter", "无遇敌"],
      ["fastText", "快速文字"], ["eventSpeed", "事件加速"], ["msgSkip", "对话快进"]
    ]]
  ];
  var battleFlags = [
    ["godMode", "无敌"], ["oneHit", "一击必杀"], ["alwaysCrit", "必定暴击"],
    ["escape", "必定逃跑成功"], ["freeSkill", "技能不消耗"]
  ];
  function escapeHtml(value) { return String(value == null ? "" : value).replace(/[&<>'"]/g, function (c) { return ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;"})[c]; }); }
  function inputValue(id) { var node = root && root.querySelector("#" + id); return node ? node.value : ""; }
  function checked(id) { var node = root && root.querySelector("#" + id); return !!(node && node.checked); }
  function readEquipScroll() {
    if (!root) return null;
    var content = root.querySelector(".tm-content");
    var slots = root.querySelector(".tm-equip-slots");
    var candidates = root.querySelector(".tm-equip-candidates");
    return {
      content: content ? content.scrollTop : 0,
      slots: slots ? slots.scrollTop : 0,
      candidates: candidates ? candidates.scrollTop : 0
    };
  }
  function restoreEquipScroll(position) {
    if (!root || !position) return;
    var content = root.querySelector(".tm-content");
    var slots = root.querySelector(".tm-equip-slots");
    var candidates = root.querySelector(".tm-equip-candidates");
    if (content) content.scrollTop = position.content;
    if (slots) slots.scrollTop = position.slots;
    if (candidates) candidates.scrollTop = position.candidates;
  }
  function systemSwitchOn(value) { return value === true || value === 1 || String(value).toLowerCase() === "true" || String(value) === "1"; }
  function isDashPlaceholder(value) {
    var name = String(value == null ? "" : value).replace(/\s+/g, "");
    return !!name && /^[\-‐‑‒–—―−﹣－_＿]+$/.test(name);
  }
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
  function card(title, body, cls, headerControl) {
    var heading = headerControl
      ? '<div class="tm-card-heading"><h3>' + escapeHtml(title) + '</h3>' + headerControl + '</div>'
      : '<h3>' + escapeHtml(title) + '</h3>';
    return '<section class="tm-card' + (cls ? " " + cls : "") + '">' + heading + body + '</section>';
  }
  function hideEmptyToggle(action, isChecked) {
    return '<label class="tm-hide-empty-toggle"><input class="tm-switch" type="checkbox" data-action="' + action + '" ' + (isChecked ? "checked" : "") + '><span>' + escapeHtml(translated("隐藏空选项")) + '</span></label>';
  }
  function row(label, control) { return '<div class="tm-row"><label>' + escapeHtml(label) + '</label>' + control + '</div>'; }
  function button(text, action, extra, data) { return '<button class="tm-btn ' + (extra || "") + '" data-action="' + action + '" ' + (data || "") + '>' + escapeHtml(text) + '</button>'; }
  function textInput(id, value, type, attrs) { return '<input class="tm-input" id="' + id + '" type="' + (type || "text") + '" value="' + escapeHtml(value == null ? "" : value) + '" ' + (attrs || "") + '>'; }
  function fitGoldInput(input) {
    if (!input) return;
    var baseWidth = 94;
    var maxWidth = 220;
    var textWidth = 0;
    try {
      var style = window.getComputedStyle(input);
      var canvas = fitGoldInput.canvas || (fitGoldInput.canvas = document.createElement("canvas"));
      var measure = canvas.getContext("2d");
      if (measure) {
        measure.font = [style.fontStyle, style.fontVariant, style.fontWeight, style.fontSize, style.fontFamily].join(" ");
        textWidth = measure.measureText(String(input.value || "")).width;
      }
    } catch (_) {}
    var style = window.getComputedStyle(input);
    var horizontalSpace = parseFloat(style.paddingLeft) + parseFloat(style.paddingRight) +
      parseFloat(style.borderLeftWidth) + parseFloat(style.borderRightWidth);
    if (!isFinite(horizontalSpace)) horizontalSpace = 20;
    var desiredWidth = Math.max(baseWidth, Math.ceil(textWidth + horizontalSpace));
    input.style.width = Math.min(desiredWidth, maxWidth) + "px";
  }
  function applyGoldInput(input) {
    if (!input) return;
    var rawValue = String(input.value || "").trim();
    var value = Number(rawValue);
    if (!rawValue || !isFinite(value) || value < 0) {
      toast("请输入有效的金币数量", true);
      return;
    }
    value = Math.floor(value);
    if ((context.gold != null && Number(context.gold) === value) || goldCommitPending === value) return;
    goldCommitPending = value;
    return run(function () { return mod.setGold(value); }, function () {
      context.gold = value;
      var current = root && root.querySelector("#tm-gold-current");
      if (current) current.textContent = String(value);
      if (input.isConnected) {
        input.value = String(value);
        fitGoldInput(input);
      }
      toast("金币已修改");
    }).then(function () {
      if (goldCommitPending === value) goldCommitPending = null;
    });
  }
  function segmentedTabs(items, selected, action) {
    return '<div class="tm-segmented-tabs">' + items.map(function (item) {
      return '<button class="tm-segmented-tab' + (item.id === selected ? ' active' : '') + '" data-action="' + action + '" data-section="' + escapeHtml(item.id) + '">' + escapeHtml(item.label) + '</button>';
    }).join("") + '</div>';
  }
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
    var groups = flagGroups.map(function (group) {
      var entries = group[1].filter(function (entry) {
        var capability = entry[0] === "godMode" ? "invincible" : entry[0] === "alwaysCrit" ? "crit" : entry[0];
        return !supported || supported.indexOf(capability) >= 0;
      });
      return { id: group[0], label: group[0], entries: entries };
    }).filter(function (group) { return group.entries.length > 0 || group.id === "探索"; });
    if (!groups.length) groups.push({ id: "队伍", label: "队伍", entries: [] });
    var sections = groups;
    if (!sections.some(function (section) { return section.id === context.quickSection; })) context.quickSection = sections[0].id;
    var selectedGroup = groups.filter(function (group) { return group.id === context.quickSection; })[0];
    var cachedGold = context.gold == null ? "…" : context.gold;
    var gold = '<div class="tm-row tm-gold-row"><label>金币：<span id="tm-gold-current">' + cachedGold + '</span></label>' +
      textInput("tm-gold", cachedGold, "number", 'min="0"') +
      button("拉满", "max-gold") + '</div>';
    var sectionBody;
    if (selectedGroup) {
      var toggles = selectedGroup.entries.map(function (entry) {
        return row(entry[1], '<input class="tm-switch" type="checkbox" data-action="flag" data-flag="' + entry[0] + '" ' + (state[entry[0]] ? "checked" : "") + '>');
      }).join("");
      var speedControls = "";
      if (selectedGroup.id === "探索") {
        if (!supported || supported.indexOf("speed") >= 0) {
          speedControls += card("移动速度", '<div class="tm-row"><input class="tm-input" id="tm-speed" type="number" min="0.25" max="8" step="0.25" value="' + escapeHtml(state.speedMultiplier || 2) + '">' + button("设置", "set-speed", "primary") + '</div>');
        }
        speedControls += card("游戏倍速", '<div class="tm-row"><input class="tm-input" id="tm-game-speed" type="number" min="1" max="9" step="1" value="' + escapeHtml(state.gameSpeedMultiplier || 1) + '">' + button("设置", "set-game-speed", "primary") + '</div>');
        if (speedControls) speedControls = '<div class="tm-grid tm-speed-controls">' + speedControls + '</div>';
      }
      var sectionContent = selectedGroup.id === "角色与资源" ? gold + toggles : toggles;
      sectionBody = card(selectedGroup.label, sectionContent);
      sectionBody += speedControls;
    } else {
      sectionBody = card(selectedGroup.label, '<div class="tm-note">当前没有可用的快捷功能</div>');
    }
    content.innerHTML = segmentedTabs(sections, context.quickSection, "quick-section") + '<div id="tm-quick-section">' + sectionBody + '</div>';
    fitGoldInput(root.querySelector("#tm-gold"));
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
          if (input && document.activeElement !== input) { input.value = String(value); fitGoldInput(input); }
        } catch (error) {
          console.warn("RGSS gold refresh failed", error);
        }
      }, 0);
    ensureCapabilities();
  }
  function ensureCapabilities() {
    if (context.capabilities || context.capabilitiesLoading) return;
    context.capabilitiesLoading = true;
    setTimeout(function () {
      try {
        var loaded = mod.listCapabilities();
        if (loaded && loaded.engine !== "unknown" && Array.isArray(loaded.toggles)) {
          context.capabilities = loaded;
          if (loaded.engine === "essentials" && window.TyranorModUI &&
              typeof window.TyranorModUI.onRuntimeEngineDetected === "function") {
            window.TyranorModUI.onRuntimeEngineDetected(loaded);
          }
        }
      } catch (error) {
        console.warn("RGSS cheat capability read failed", error);
      }
      context.capabilitiesLoading = false;
      if (!context.capabilities || !root) return;
      if (activeTab === "quick") renderQuick(root.querySelector(".tm-content"), false);
      else if (activeTab === "battle") renderBattle(root.querySelector(".tm-content"));
    }, 0);
  }
  function itemKindOptionsMarkup() {
    var itemKinds = [["item", "物品"], ["weapon", "武器"], ["armor", "护甲"]];
    return itemKinds.map(function (entry) {
      var selected = context.itemKind === entry[0];
      return '<button type="button" class="tm-system-kind-option' + (selected ? ' active' : '') + '" role="option" aria-selected="' + (selected ? 'true' : 'false') + '" data-action="select-item-kind" data-kind="' + entry[0] + '">' + escapeHtml(translated(entry[1])) + '</button>';
    }).join("");
  }
  function renderItems(content) {
    var itemKinds = [["item", "物品"], ["weapon", "武器"], ["armor", "护甲"]];
    var selectedKind = itemKinds.filter(function (entry) { return entry[0] === context.itemKind; })[0] || itemKinds[0];
    var kinds = '<div class="tm-system-kind-picker tm-item-kind-picker"><button type="button" class="tm-system-kind-trigger" data-action="toggle-item-kind" aria-haspopup="listbox" aria-expanded="' + (context.itemKindExpanded ? 'true' : 'false') + '"><span>' + escapeHtml(translated(selectedKind[1])) + '</span><span class="tm-system-kind-arrow" aria-hidden="true">▾</span></button>' +
      (context.itemKindExpanded ? '<div class="tm-system-kind-menu" role="listbox">' + itemKindOptionsMarkup() + '</div>' : '') + '</div>';
    var tools = '<div class="tm-row">' + kinds + textInput("tm-item-query", context.itemQuery, "search", 'placeholder="' + escapeHtml(translated("搜索名称")) + '"') + button(translated("搜索"), "render-items", "primary") + '</div>';
    var header = '<div class="tm-lock-list-header tm-item-lock-header"><span>' + escapeHtml(translated("锁定")) + '</span><span>' + escapeHtml(translated("物品")) + '</span><span>' + escapeHtml(translated("数量")) + '</span></div>';
    content.innerHTML = '<div class="tm-grid tm-item-layout">' +
      card(translated("物品数据库"), tools + header + '<div id="tm-item-list" class="tm-list"><div class="tm-note">' + escapeHtml(translated("加载中…")) + '</div></div>', "tm-item-roster") +
      '<section class="tm-card tm-item-detail" id="tm-item-detail"></section>' +
      '</div>';
    loadItems();
  }
  function loadItems() {
    context.itemQuery = inputValue("tm-item-query");
    var result = mod.listDatabase(context.itemKind, context.itemQuery, 0, 200);
    var list = root.querySelector("#tm-item-list");
    context.itemEntries = (result.values || []).filter(function (item) { return !isDashPlaceholder(item.name); });
    if (!context.itemEntries.some(function (item) { return Number(item.id) === Number(context.selectedItemId); })) {
      context.selectedItemId = context.itemEntries.length ? Number(context.itemEntries[0].id) : null;
    }
    list.innerHTML = context.itemEntries.length ? context.itemEntries.map(function (item) {
      var selected = Number(item.id) === Number(context.selectedItemId);
      return '<div class="tm-item tm-lock-row tm-item-lock-row' + (selected ? ' selected' : '') + '" data-action="select-item" data-id="' + escapeHtml(item.id) + '"><label class="tm-lock-cell" aria-label="' + escapeHtml(translated("锁定") + " #" + item.id + " " + item.name) + '"><input class="tm-lock-checkbox" type="checkbox" data-action="lock-item" data-id="' + escapeHtml(item.id) + '" ' + (item.locked ? "checked" : "") + '></label><button type="button" class="tm-item-select" data-action="select-item" data-id="' + escapeHtml(item.id) + '" aria-pressed="' + (selected ? "true" : "false") + '"><span class="tm-name" title="' + escapeHtml(item.name) + '">#' + item.id + ' ' + escapeHtml(item.name) + '</span>' +
        '<span class="tm-badge tm-item-count">' + escapeHtml(item.count) + '</span></button></div>';
    }).join("") : '<div class="tm-note">' + escapeHtml(translated("没有匹配项目")) + '</div>';
    renderItemDetail();
  }
  function renderItemDetail() {
    var detail = root && root.querySelector("#tm-item-detail");
    if (!detail) return;
    var item = (context.itemEntries || []).filter(function (entry) { return Number(entry.id) === Number(context.selectedItemId); })[0];
    if (!item) {
      detail.innerHTML = '<h3>' + escapeHtml(translated("物品详情")) + '</h3><div class="tm-note">' + escapeHtml(translated("请选择物品查看详情")) + '</div>';
      return;
    }
    var description = String(item.description || "").trim() || "—";
    detail.innerHTML = '<div class="tm-item-detail-heading"><h3>' + escapeHtml(translated("物品详情")) + '</h3><span class="tm-badge">#' + escapeHtml(item.id) + '</span></div>' +
      '<div class="tm-item-detail-info"><div class="tm-item-detail-name"><span>' + escapeHtml(translated("名称:")) + '</span><strong>' + escapeHtml(item.name || "—") + '</strong></div>' +
      '<div class="tm-item-detail-description"><span>' + escapeHtml(translated("描述:")) + '</span><p>' + escapeHtml(description) + '</p></div></div>' +
      '<section class="tm-item-quantity-card"><div class="tm-item-quantity-heading"><h4>' + escapeHtml(translated("数量操作")) + '</h4><div class="tm-item-current-count"><span>' + escapeHtml(translated("当前数量")) + '</span><strong>' + escapeHtml(item.count) + '</strong></div></div>' +
      '<div class="tm-item-quantity-control">' + textInput("tm-detail-count", item.count, "number", 'min="0" inputmode="numeric" aria-label="' + escapeHtml(translated("数量操作")) + '"') + button(translated("应用"), "set-item", "primary", 'data-id="' + escapeHtml(item.id) + '"') + '</div></section>';
  }
  function renderActors(content) {
    var actors = mod.listActors();
    if (!actors.length) { content.innerHTML = '<div class="tm-note">当前游戏没有角色</div>'; return; }
    context.actorEntries = actors;
    if (!context.actorId || !actors.some(function (a) { return a.id === context.actorId; })) context.actorId = actors[0].id;
    content.innerHTML = '<div class="tm-grid tm-actor-layout">' +
      card("队伍管理", textInput("tm-actor-query", context.actorQuery, "search", 'placeholder="搜索角色名称或 ID"') + '<div id="tm-actor-list" class="tm-list tm-actor-list"></div>', "tm-actor-roster") +
      '<section class="tm-card tm-actor-detail" id="tm-actor-body"></section>' +
      '</div>';
    renderActorRoster();
    renderActorBody(actors);
  }
  function renderActorRoster() {
    var list = root && root.querySelector("#tm-actor-list");
    if (!list) return;
    var query = String(context.actorQuery || "").trim().toLowerCase();
    var entries = (context.actorEntries || []).filter(function (actor) {
      return !query || String(actor.name).toLowerCase().indexOf(query) >= 0 || String(actor.id).indexOf(query) >= 0;
    });
    list.innerHTML = entries.map(function (actor) {
      var selected = actor.id === context.actorId ? " selected" : "";
      return '<div class="tm-item tm-actor-row' + selected + '">' +
        '<button type="button" class="tm-actor-select" data-action="actor-select" data-id="' + actor.id + '">#' + actor.id + ' ' + escapeHtml(actor.name) + '</button>' +
        '<label class="tm-actor-party"><input type="checkbox" data-action="actor-party" data-id="' + actor.id + '" ' + (actor.inParty ? "checked" : "") + '>在队伍中</label>' +
        '</div>';
    }).join("") || '<div class="tm-note">没有匹配项目</div>';
  }
  function renderActorBody(actors) {
    actors = actors || mod.listActors();
    context.actorEntries = actors;
    var actor = actors.filter(function (a) { return a.id === context.actorId; })[0];
    var body = root.querySelector("#tm-actor-body");
    if (!body) return;
    if (!actor) { body.innerHTML = '<div class="tm-note">请先选择角色</div>'; return; }
    var sections = [
      { id: "stats", label: "属性" }, { id: "skills", label: "技能" },
      { id: "states", label: "状态" }, { id: "equips", label: "装备" }
    ];
    body.innerHTML = '<div class="tm-actor-detail-heading"><h3>' + escapeHtml(actor.name) + '</h3><span class="tm-badge">' + (actor.inParty ? "在队伍中" : "不在队伍中") + '</span>' + button("恢复", "recover-actor", "primary") + '</div>' +
      segmentedTabs(sections, context.actorSection, "actor-section") + '<div id="tm-actor-section"></div>';
    renderActorSection();
  }
  function positionActorClassMenu() {
    var menu = root && root.querySelector(".tm-actor-class-menu");
    var trigger = root && root.querySelector("#tm-actor-class");
    if (!menu || !trigger) return;
    var rect = trigger.getBoundingClientRect();
    var viewportWidth = window.innerWidth || document.documentElement.clientWidth;
    var viewportHeight = window.innerHeight || document.documentElement.clientHeight;
    var edge = 12;
    var gap = 4;
    var width = Math.max(0, Math.min(rect.width, viewportWidth - edge * 2));
    var left = Math.max(edge, Math.min(rect.left, viewportWidth - edge - width));
    var roomBelow = Math.max(0, viewportHeight - edge - rect.bottom - gap);
    var roomAbove = Math.max(0, rect.top - edge - gap);
    var desiredHeight = Math.min(220, menu.scrollHeight);
    var openAbove = roomBelow < desiredHeight && roomAbove > roomBelow;
    var availableHeight = Math.min(220, openAbove ? roomAbove : roomBelow);
    menu.style.position = "fixed";
    menu.style.left = left + "px";
    menu.style.right = "auto";
    menu.style.width = width + "px";
    menu.style.maxHeight = availableHeight + "px";
    menu.style.top = openAbove ? "auto" : (rect.bottom + gap) + "px";
    menu.style.bottom = openAbove ? (viewportHeight - rect.top + gap) + "px" : "auto";
  }
  function renderActorSection() {
    var actor = (context.actorEntries || []).filter(function (a) { return a.id === context.actorId; })[0];
    var section = root.querySelector("#tm-actor-section");
    if (!actor || !section) return;
    root.querySelectorAll(".tm-segmented-tab[data-action='actor-section']").forEach(function (tab) {
      tab.classList.toggle("active", tab.dataset.section === context.actorSection);
    });
    if (context.actorSection === "stats") {
      var classData = mod.actorClasses(actor.id) || { currentId: 0, classes: [] };
      var classOptions = classData.classes || [];
      var currentClass = classOptions.filter(function (entry) { return Number(entry.id) === Number(classData.currentId); })[0];
      var classOptionsHtml = classOptions.map(function (entry) {
        var selected = Number(entry.id) === Number(classData.currentId);
        return '<button type="button" class="tm-system-kind-option' + (selected ? ' active' : '') + '" role="option" aria-selected="' + (selected ? 'true' : 'false') + '" data-action="select-actor-class" data-id="' + Number(entry.id) + '">' + escapeHtml(entry.name) + '</button>';
      }).join("");
      var classPicker = '<div class="tm-system-kind-picker tm-actor-class-picker"><button type="button" id="tm-actor-class" class="tm-system-kind-trigger" data-action="toggle-actor-class" aria-haspopup="listbox" aria-expanded="' + (context.actorClassExpanded ? 'true' : 'false') + '" aria-label="' + escapeHtml(translated("职业")) + '"><span>' + escapeHtml(currentClass ? currentClass.name : translated("选择职业")) + '</span><span class="tm-system-kind-arrow" aria-hidden="true">▾</span></button>' +
        (context.actorClassExpanded ? '<div class="tm-system-kind-menu tm-actor-class-menu" role="listbox">' + classOptionsHtml + '</div>' : '') + '</div>';
      var classRow = classOptions.length
        ? '<div class="tm-item tm-actor-class-row"><span class="tm-name">' + escapeHtml(translated("职业")) + '</span>' + classPicker + '</div>'
        : '<div class="tm-note">' + escapeHtml(translated("当前游戏没有职业列表")) + '</div>';
      var statLabels = { level: "等级", exp: "经验", hp: "HP", mp: "MP", mhp: "最大 HP", mmp: "最大 MP", atk: "攻击", def: "防御", mat: "魔攻", mdf: "魔防", agi: "敏捷", luk: "幸运" };
      var stats = ["level","exp","hp","mp","mhp","mmp","atk","def","mat","mdf","agi","luk"].map(function (key) {
      return '<div class="tm-item"><span class="tm-name">' + escapeHtml(translated(statLabels[key] || key.toUpperCase())) + '</span>' + textInput("tm-stat-" + key, actor[key], "number", 'style="width:94px"') + button("设置", "set-stat", "", 'data-key="' + key + '"') + '</div>';
      }).join("");
      section.innerHTML = card(translated("属性"), classRow + '<div class="tm-list">' + stats + '</div>');
      var classMenu = section.querySelector(".tm-actor-class-menu");
      if (classMenu) positionActorClassMenu();
    } else if (context.actorSection === "skills") {
      context.actorSkillEntries = mod.actorSkills(actor.id) || [];
      var skillControls = textInput("tm-skill-query", context.skillQuery, "search", 'placeholder="' + escapeHtml(translated("搜索技能名称或 ID")) + '"') +
        '<div class="tm-actor-collection-columns">' +
        card(translated("已学会技能"), '<div id="tm-skill-known" class="tm-list tm-actor-collection-list"></div>', "tm-actor-collection-card") +
        card(translated("可学习技能"), '<div id="tm-skill-available" class="tm-list tm-actor-collection-list"></div>', "tm-actor-collection-card") + '</div>';
      section.innerHTML = card(translated("技能"), skillControls, "tm-actor-collection-page", hideEmptyToggle("toggle-hide-empty-skills", context.hideEmptySkills));
      renderActorCollectionList("skills");
    } else if (context.actorSection === "states") {
      context.actorStateEntries = mod.actorStates(actor.id) || [];
      var stateControls = textInput("tm-state-query", context.stateQuery, "search", 'placeholder="' + escapeHtml(translated("搜索状态名称或 ID")) + '"') +
        '<div class="tm-actor-collection-columns">' +
        card(translated("当前状态"), '<div id="tm-state-active" class="tm-list tm-actor-collection-list"></div>', "tm-actor-collection-card") +
        card(translated("可附加状态"), '<div id="tm-state-available" class="tm-list tm-actor-collection-list"></div>', "tm-actor-collection-card") + '</div>';
      section.innerHTML = card(translated("状态"), stateControls, "tm-actor-collection-page");
      renderActorCollectionList("states");
    } else {
      renderActorEquipments(actor, section);
    }
  }
  function renderActorEquipments(actor, section) {
    var equips = mod.actorEquips(actor.id) || [];
    if (!equips.length) {
      section.innerHTML = card(translated("装备"), '<div class="tm-note">' + escapeHtml(translated("当前角色没有可修改的装备槽位")) + '</div>');
      return;
    }
    var slot = equips.filter(function (entry) { return Number(entry.slot) === Number(context.equipSlot); })[0] || equips[0] || null;
    context.equipSlot = slot ? Number(slot.slot) : null;
    context.equipKind = slot && slot.type === "weapon" ? "weapon" : "armor";
    var candidates = slot ? (mod.equipCandidates(actor.id, Number(slot.slot)) || []) : [];
    var currentId = slot ? Number(slot.id) || 0 : 0;
    var currentKind = slot ? slot.type : context.equipKind;
    var currentRawName = slot && slot.name && slot.name !== "None" ? String(slot.name) : "";
    var currentName = slot && slot.name && slot.name !== "None" ? slot.name : translated("无");
    var available = candidates.filter(function (entry) {
      return entry.kind === context.equipKind && Number(entry.id) > 0 && !isDashPlaceholder(entry.name) &&
        (!context.hideEmptyEquipCandidates || String(entry.name || "").trim().length > 0);
    });
    var currentCandidateExists = candidates.some(function (entry) { return entry.kind === currentKind && Number(entry.id) === currentId; });
    if (slot && currentId > 0 && currentKind === context.equipKind && !currentCandidateExists && !isDashPlaceholder(currentRawName) &&
      (!context.hideEmptyEquipCandidates || currentRawName.trim().length > 0)) {
      available.unshift({ kind: currentKind, id: currentId, name: currentName });
    }
    var currentRows = equips.map(function (entry) {
      var entryType = entry.type === "weapon" ? "武器" : "护甲";
      var entrySlotName = entry.slotName || translated(entryType);
      var itemName = entry.name && entry.name !== "None" ? entry.name : translated("无");
      var selected = slot && Number(entry.slot) === Number(slot.slot);
      var unequip = Number(entry.id) > 0 ? button(translated("卸下动作"), "unequip-equip", "tm-equip-action", 'data-id="' + Number(entry.slot) + '"') : '<span class="tm-badge tm-equip-action">' + escapeHtml(translated("无")) + '</span>';
      return '<div class="tm-item tm-equip-slot-row' + (selected ? ' selected' : '') + '"><button type="button" class="tm-equip-slot-option" data-action="pick-equip-slot" data-id="' + Number(entry.slot) + '"><span class="tm-equip-slot-name">' + escapeHtml(translated("槽位" + entry.slot + "：") + entrySlotName) + '</span><span class="tm-equip-slot-item">' + escapeHtml(itemName) + '</span></button>' + unequip + '</div>';
    }).join("");
    var itemRows = available.map(function (entry) {
      var equipped = Number(entry.id) === currentId && currentKind === context.equipKind;
      return '<div class="tm-item tm-equip-option"><span class="tm-equip-option-name">#' + Number(entry.id) + ' ' + escapeHtml(entry.name) + '</span>' +
        (equipped ? '<span class="tm-badge tm-equip-action">' + escapeHtml(translated("当前装备")) + '</span>' : button(translated("装备动作"), "equip-candidate", "primary tm-equip-action", 'data-id="' + Number(entry.id) + '"')) + '</div>';
    }).join("") || '<div class="tm-note">' + escapeHtml(translated(slot ? "当前槽位没有可选装备" : "当前角色没有此类槽位")) + '</div>';
    section.innerHTML = '<div class="tm-equip-page"><div class="tm-equip-columns">' +
      card(translated("当前装备"), '<div class="tm-list tm-equip-slots">' + currentRows + '</div>', "tm-equip-data-card") +
      card(translated(context.equipKind === "weapon" ? "可选武器" : "可选护甲"), '<small class="tm-equip-hint">' + escapeHtml(translated("选择左侧槽位后，点击装备")) + '</small><div class="tm-list tm-equip-candidates">' + itemRows + '</div>', "tm-equip-data-card", hideEmptyToggle("toggle-hide-empty-equips", context.hideEmptyEquipCandidates)) +
      '</div></div>';
  }
  function renderActorCollectionList(kind) {
    var isSkill = kind === "skills";
    var knownList = root.querySelector(isSkill ? "#tm-skill-known" : "#tm-state-active");
    var availableList = root.querySelector(isSkill ? "#tm-skill-available" : "#tm-state-available");
    if (!knownList || !availableList) return;
    var query = String(inputValue(isSkill ? "tm-skill-query" : "tm-state-query") || "").trim().toLowerCase();
    var entries = isSkill ? context.actorSkillEntries : context.actorStateEntries;
    var matches = (entries || []).filter(function (entry) {
      var name = String(entry.name || "");
      if (isDashPlaceholder(name)) return false;
      if (isSkill && context.hideEmptySkills && !name.trim()) return false;
      return !query || name.toLowerCase().indexOf(query) >= 0 || String(entry.id).indexOf(query) >= 0;
    });
    function renderRows(rows, enabled) {
      var action = isSkill ? (enabled ? "forget-skill" : "learn-skill") : (enabled ? "remove-state" : "add-state");
      var label = isSkill ? (enabled ? "遗忘" : "学习") : (enabled ? "移除" : "附加");
      return rows.map(function (entry) {
        return '<div class="tm-item"><span class="tm-name">#' + entry.id + ' ' + escapeHtml(entry.name) + '</span>' + button(translated(label), action, enabled ? "" : "primary", 'data-id="' + entry.id + '"') + '</div>';
      }).join("") || '<div class="tm-note">' + escapeHtml(translated(query ? "没有匹配项目" : (isSkill ? (enabled ? "没有已学技能" : "没有可学习技能") : (enabled ? "没有当前状态" : "没有可附加状态")))) + '</div>';
    }
    knownList.innerHTML = renderRows(matches.filter(function (entry) { return !!entry.enabled; }), true);
    availableList.innerHTML = renderRows(matches.filter(function (entry) { return !entry.enabled; }), false);
  }
  function refreshActorCollection(kind, entries) {
    if (kind === "skills") context.actorSkillEntries = entries || [];
    else context.actorStateEntries = entries || [];
    renderActorCollectionList(kind);
  }
  function renderSystem(content) {
    var kinds = [["switch", "开关"], ["variable", "变量"]];
    var selectedKind = translated(context.systemKind === "switch" ? "开关" : "变量");
    var options = kinds.map(function (entry) {
      return '<button type="button" class="tm-system-kind-option' + (context.systemKind === entry[0] ? ' active' : '') + '" role="option" aria-selected="' + (context.systemKind === entry[0] ? 'true' : 'false') + '" data-action="select-system-kind" data-kind="' + entry[0] + '">' + escapeHtml(translated(entry[1])) + '</button>';
    }).join("");
    var kind = '<div class="tm-system-kind-picker"><button type="button" class="tm-system-kind-trigger" data-action="toggle-system-kind" aria-haspopup="listbox" aria-expanded="' + (context.systemKindExpanded ? 'true' : 'false') + '"><span>' + escapeHtml(selectedKind) + '</span><span class="tm-system-kind-arrow" aria-hidden="true">▾</span></button>' +
      (context.systemKindExpanded ? '<div class="tm-system-kind-menu" role="listbox">' + options + '</div>' : '') + '</div>';
    var header = '<div class="tm-lock-list-header tm-system-lock-header"><span>锁定</span><span>名称</span><span>当前值</span></div>';
    content.innerHTML = card("开关与变量", '<div class="tm-row">' + kind + textInput("tm-system-query", context.systemQuery, "search", 'placeholder="名称或 ID"') + '</div>' + header + '<div id="tm-system-list" class="tm-list"></div>', "tm-fill-card", hideEmptyToggle("toggle-hide-empty-system", context.hideEmptySystemEntries));
    loadSystem();
  }
  function loadSystem() {
    context.systemQuery = inputValue("tm-system-query");
    var includeUnnamed = !context.hideEmptySystemEntries;
    var result = context.systemKind === "switch" ? mod.listSwitches(context.systemQuery, 0, 200, includeUnnamed) : mod.listVariables(context.systemQuery, 0, 200, includeUnnamed);
    root.querySelector("#tm-system-list").innerHTML = result.values.filter(function (item) {
      if (isDashPlaceholder(item.name)) return false;
      return !context.hideEmptySystemEntries || item.named !== false;
    }).map(function (item) {
      var name = String(item.name || (context.systemKind === "switch" ? "开关 " : "变量 ") + item.id);
      var isSwitch = context.systemKind === "switch";
      var switchOn = isSwitch && systemSwitchOn(item.value);
      var control = context.systemKind === "switch" ?
        '<label class="tm-system-switch"><span class="tm-system-switch-state">' + escapeHtml(translated(switchOn ? "开" : "关")) + '</span><input class="tm-switch tm-system-switch-input" id="tm-system-' + item.id + '" type="checkbox" data-action="set-system-switch" data-id="' + item.id + '" data-current-value="' + (switchOn ? "1" : "0") + '" ' + (switchOn ? "checked" : "") + '></label>' :
        '<input class="tm-input tm-system-value" id="tm-system-' + item.id + '" type="text" data-action="set-system" data-id="' + item.id + '" value="' + escapeHtml(typeof item.value === "object" ? JSON.stringify(item.value) : item.value) + '" aria-label="' + escapeHtml(name) + '">';
      return '<div class="tm-item tm-lock-row tm-system-lock-row' + (isSwitch ? ' tm-system-switch-row' : '') + '"><label class="tm-lock-cell"><input class="tm-lock-checkbox" type="checkbox" data-action="lock-system" data-kind="' + context.systemKind + '" data-id="' + item.id + '" ' + (item.locked ? "checked" : "") + '></label><span class="tm-name" title="#' + item.id + ' ' + escapeHtml(name) + '">#' + item.id + ' ' + escapeHtml(name) + '</span>' + control + '</div>';
    }).join("") || '<div class="tm-note">没有匹配项目</div>';
  }
  function renderMap(content) {
    var pos = mod.currentPosition();
    context.mapListExpanded = false;
    context.currentMapId = Number(pos && pos.mapId) || 0;
    context.selectedMapId = context.currentMapId;
    context.eventId = 0;
    var currentMapLabel = mapLabel(context.selectedMapId);
    var mapPicker = '<button class="tm-btn tm-map-picker" data-action="toggle-map-list" title="' + escapeHtml(currentMapLabel) + '"><span class="tm-map-picker-label">' + escapeHtml(currentMapLabel) + '</span><span class="tm-map-picker-arrow" aria-hidden="true">▾</span></button>';
    var browseRow = '<div class="tm-map-picker-row">' + mapPicker + '</div><div class="tm-row">' + textInput("tm-browse-query", "", "search", 'placeholder="搜索"') + button("搜索", "render-browse", "primary") + '</div>';
    var mapControls = '<div class="tm-row tm-map-teleport-row">' + textInput("tm-map-id", pos && pos.mapId, "number", 'placeholder="地图 ID"') + textInput("tm-map-x", pos && pos.x, "number", 'placeholder="X"') + textInput("tm-map-y", pos && pos.y, "number", 'placeholder="Y"') + button("传送", "teleport", "primary") + '</div>'
      + '<div class="tm-map-action-buttons">' + button("迷你地图", "show-minimap") + button("事件开关全开", "map-selfswitches", "", 'data-enabled="1"') + button("事件开关全关", "map-selfswitches", "", 'data-enabled="0"') + '</div>';
    var teleportCard = card("地图操作", mapControls);
    var eventCard = card("事件详情", '<div class="tm-card-body" id="tm-event-panel">' + eventPanelHtml() + '</div>');
    content.innerHTML = '<div class="tm-grid tm-map-grid">'
      + '<section class="tm-card tm-browse-card"><h3>浏览</h3>' + browseRow + '<div class="tm-list" id="tm-browse-list"></div><div class="tm-list tm-map-dropdown" id="tm-map-list" style="display:none"></div></section>'
      + teleportCard + eventCard + '</div>';
    loadEventsForMap(context.selectedMapId);
    loadBrowse();
  }
  function mapLabel(mapId) {
    var label = "#" + ("0000" + (Number(mapId) || 0)).slice(-4);
    if (!mapId) return label;
    try {
      var maps = mod.listMaps(String(mapId), 0, 300).values || [];
      var map = maps.filter(function (entry) { return Number(entry.id) === Number(mapId); })[0];
      if (map && map.name) label += ": " + map.name;
    } catch (_) {}
    return label;
  }
  function loadEventsForMap(mapId) {
    context.eventAll = [];
    try { context.eventAll = mod.listEvents(Number(mapId) || 0).values || []; } catch (_) {}
  }
  function eventConditionEntriesHtml(page) {
    var conditions = page.conditions || [];
    if (!conditions.length) return '<div class="tm-note">' + escapeHtml(translated("无条件")) + '</div>';
    return '<div class="tm-event-condition-list tm-event-condition-dialog-list">' + conditions.map(function (condition) {
      var label;
      var current = condition.current === "true" ? translated("开") : condition.current === "false" ? translated("关") : condition.current;
      var target = condition.target === "true" ? translated("开") : condition.target === "false" ? translated("关") : condition.target;
      if (condition.type === "switch") label = translated("开关") + " " + condition.id + " · " + (condition.name || "") + " = " + target;
      else if (condition.type === "variable") label = translated("变量") + " " + condition.id + " · " + (condition.name || "") + " " + target;
      else if (condition.type === "self_switch") label = translated("独立开关") + " " + condition.id + " = " + target;
      else if (condition.type === "item") label = translated("物品") + " " + condition.id + " · " + (condition.name || "") + " " + condition.target;
      else if (condition.type === "actor") label = translated("角色") + " " + condition.id + " · " + (condition.name || "") + " " + translated(condition.current === "true" ? "在队伍中" : "不在队伍中");
      else label = (condition.name || condition.type) + " " + condition.target;
      var toggleable = condition.type === "switch" || condition.type === "self_switch";
      var tag = toggleable ? "button" : "div";
      var toggleAttrs = toggleable
        ? ' type="button" data-action="toggle-event-condition" data-condition-type="' + condition.type + '" data-id="' + escapeHtml(condition.id) + '" data-enabled="' + (condition.current === "true" ? "0" : "1") + '" data-page="' + page.index + '"'
        : "";
      return '<' + tag + ' class="tm-badge ' + (condition.met ? "tm-on" : "tm-off") + (toggleable ? " tm-condition-toggle" : "") + '" title="当前: ' + escapeHtml(current) + '"' + toggleAttrs + '>' + escapeHtml(label) + ' · ' + escapeHtml(current) + '</' + tag + '>';
    }).join("") + '</div>';
  }
  function fillTeleport(mapId, x, y) {
    var mi = root.querySelector("#tm-map-id"), xi = root.querySelector("#tm-map-x"), yi = root.querySelector("#tm-map-y");
    if (mi && mapId != null) mi.value = mapId;
    if (xi && x != null) xi.value = x;
    if (yi && y != null) yi.value = y;
  }
  function coordinateOrZero(value) {
    var coordinate = Number(value);
    return isFinite(coordinate) ? coordinate : 0;
  }
  function loadBrowse() {
    var queryInput = root.querySelector("#tm-browse-query");
    if (queryInput) queryInput.placeholder = context.mapListExpanded ? "搜索地图名称 / ID" : "搜索 ID / 名称";
    var mapPicker = root.querySelector(".tm-map-picker");
    if (mapPicker) {
      var label = mapLabel(context.selectedMapId);
      var labelNode = mapPicker.querySelector(".tm-map-picker-label");
      if (labelNode) labelNode.textContent = label;
      mapPicker.title = label;
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
    list.classList.add("tm-map-tree-list");
    var pos = null;
    try { pos = mod.currentPosition(); } catch (_) { pos = null; }
    var maps = [];
    try { maps = mod.listMaps(inputValue("tm-browse-query"), 0, 300).values; } catch (e) {
      list.innerHTML = '<div class="tm-note">' + escapeHtml(String(e && e.message || e)) + '</div>'; return;
    }
    context.mapEntries = maps;
    var depths = maps.map(function (map) { return Math.max(0, Number(map.depth) || 0); });
    var maxDepth = depths.reduce(function (max, depth) { return Math.max(max, depth); }, 0);
    var nextAtOrAbove = [];
    for (var level = 0; level <= maxDepth; level++) {
      var nextIndex = -1;
      var nextByRow = new Array(maps.length);
      for (var row = maps.length - 1; row >= 0; row--) {
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
      if (!depth) return "";
      var visibleDepth = depth;
      var prefix = "";
      for (var level = 0; level < visibleDepth; level++) {
        prefix += '<span class="tm-map-tree-guide' + (hasLaterAtLevel(index, level) ? " tm-guide-continuing" : "") + '"></span>';
      }
      return prefix + '<span class="tm-map-tree-branch">' + (hasLaterAtLevel(index, depth) ? "├─" : "└─") + '</span>';
    }
    list.innerHTML = maps.map(function (m, index) {
      var here = pos && Number(pos.mapId) === Number(m.id);
      var picked = Number(context.selectedMapId) === Number(m.id);
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
    }).join("") || '<div class="tm-note">所选地图没有事件</div>';
  }
  function filteredEvents() {
    var query = String(inputValue("tm-browse-query") || "").toLowerCase();
    if (!query) return context.eventAll || [];
    return (context.eventAll || []).filter(function (ev) {
      return String(ev.name).toLowerCase().indexOf(query) >= 0 || String(ev.id).indexOf(query) >= 0;
    });
  }
  function eventPanelHtml() {
    if (!context.eventId) {
      context.selectedEventDetail = null;
      return '<div class="tm-note tm-event-empty">在左侧选择事件查看详情</div>';
    }
    var detail;
    try { detail = mod.eventDetail(context.eventId, context.selectedMapId); } catch (e) { context.selectedEventDetail = null; return '<div class="tm-note tm-event-empty">' + escapeHtml(String(e && e.message || e)) + '</div>'; }
    if (!detail || detail.error) { context.selectedEventDetail = null; return '<div class="tm-note tm-event-empty">无法读取所选事件详情</div>'; }
    context.selectedEventDetail = detail;
    var pages = (detail.pages || []).map(function (page) {
      return '<div class="tm-event-page">'
        + '<div class="tm-event-page-head"><strong>事件页 ' + (Number(page.index) + 1) + '</strong><span>指令数 ' + page.commandCount + ' · 条件数 ' + page.validCount + '</span>'
        + '<div class="tm-event-page-actions">' + button(translated("事件页开关") + " (" + (page.conditions || []).length + ")", "show-event-conditions", "", 'data-page="' + page.index + '"') + button(translated("事件解释器按钮"), "show-event-interpreter", "", 'data-page="' + page.index + '"') + '</div></div>'
        + '</div>';
    }).join("") || '<div class="tm-note">事件没有可显示的页面</div>';
    var description = String(detail.description || "").trim();
    return '<div class="tm-event-summary">'
      + '<div class="tm-event-overview"><span class="tm-event-name-label">' + escapeHtml(translated("名称:")) + '</span><strong title="' + escapeHtml(detail.name) + '">' + escapeHtml(detail.name) + '</strong><span class="tm-event-pos">Pos: ' + detail.x + ', ' + detail.y + '</span></div>'
      + '<div class="tm-event-description"><small>描述:</small><span title="' + escapeHtml(description || "—") + '">' + escapeHtml(description || "—") + '</span></div>'
      + '</div><div class="tm-event-pages">' + pages + '</div>';
  }
  function renderEventPanel() {
    var panel = root.querySelector("#tm-event-panel");
    if (panel) panel.innerHTML = eventPanelHtml();
  }
  function showMinimapDialog() {
    var data;
    try { data = mod.minimapData(); } catch (e) { toast(String(e && e.message || e)); return; }
    if (!data || !(Number(data.width) > 0) || !(Number(data.height) > 0)) { toast(translated("无法读取当前地图"), true); return; }
    var lg = '<div class="tm-map-legend">'
      + '<span class="tm-lg"><i class="tm-map-legend-swatch tm-blocked"></i>' + escapeHtml(translated("无法通行")) + '</span>'
      + '<span class="tm-lg"><i class="tm-map-legend-swatch tm-passable"></i>' + escapeHtml(translated("可通行")) + '</span>'
      + '<span class="tm-lg"><i class="tm-map-legend-swatch tm-event"></i>' + escapeHtml(translated("事件")) + '</span>'
      + '<span class="tm-lg"><i class="tm-map-legend-swatch tm-player"></i>' + escapeHtml(translated("你")) + '</span>'
      + '<span class="tm-lg"><i class="tm-map-legend-swatch tm-movable"></i>' + escapeHtml(translated("敌人/可移动事件")) + '</span>'
      + '<span class="tm-lg"><i class="tm-map-legend-swatch tm-item-event"></i>' + escapeHtml(translated("宝箱/道具事件")) + '</span>'
      + '<span class="tm-lg"><i class="tm-map-legend-swatch tm-teleport"></i>' + escapeHtml(translated("传送点")) + '</span></div>';
    var modal = document.createElement("div");
    modal.className = "tm-modal";
    modal.innerHTML = '<div class="tm-modal-box tm-minimap-modal-box">'
      + '<div class="tm-modal-head"><strong>' + escapeHtml(translated("迷你地图 #") + data.mapId + " · " + data.width + "×" + data.height) + '</strong>' + lg + button("×", "close-minimap", "") + '</div>'
      + '<div class="tm-modal-body tm-minimap-modal-body"><div class="tm-minimap-viewport"><canvas class="tm-minimap" id="tm-minimap-canvas"></canvas></div>'
      + '<div class="tm-minimap-controls"><div class="tm-minimap-action-card"><span class="tm-minimap-selection" id="tm-minimap-selection">' + escapeHtml(translated("点击地图选择格子")) + '</span>'
      + '<div class="tm-minimap-actions">' + button(translated("传送"), "minimap-cell-teleport", "primary", 'disabled')
      + button(translated("事件解释器"), "minimap-cell-event-interpreter", "", 'disabled') + '</div></div>'
      + '<div class="tm-minimap-zoom-card"><div class="tm-minimap-control-head"><span>' + escapeHtml(translated("地图大小")) + '</span><strong class="tm-minimap-zoom-value">100%</strong></div>'
      + '<div class="tm-minimap-slider-row"><span>' + escapeHtml(translated("缩小")) + '</span><input id="tm-minimap-zoom" type="range" min="100" max="400" step="5" value="100" aria-label="' + escapeHtml(translated("地图大小")) + '">'
      + '<span>' + escapeHtml(translated("放大")) + '</span></div></div></div>'
      + '<div class="tm-modal-hint">' + escapeHtml(translated("点击格子选中坐标；再用下方按钮传送或查看事件；按住拖动地图可平移；点击遮罩或 × 关闭")) + '</div>'
      + '</div>';
    modal.classList.add("tm-minimap-modal");
    modal.addEventListener("click", function (e) { if (e.target === modal) closeMinimapModal(modal); });
    root.appendChild(modal);
    var canvas = modal.querySelector("#tm-minimap-canvas");
    modal._miniMapRenderer = window.TyranorMiniMap && window.TyranorMiniMap.mount({
      canvas: canvas,
      viewport: canvas.parentElement,
      slider: modal.querySelector("#tm-minimap-zoom"),
      zoomValue: modal.querySelector(".tm-minimap-zoom-value"),
      data: data,
      onCell: function (x, y, events) {
        context.minimapSelection = { mapId: Number(data.mapId) || context.currentMapId, x: x, y: y, events: events || [] };
        var selectionLabel = modal.querySelector("#tm-minimap-selection");
        var actions = modal.querySelectorAll(".tm-minimap-actions [data-action]");
        if (selectionLabel) selectionLabel.textContent = translated("已选格") + " (" + x + ", " + y + ")";
        if (actions[0]) actions[0].disabled = false;
        if (actions[1]) actions[1].disabled = false;
      },
      onClear: function () {
        context.minimapSelection = null;
        var selectionLabel = modal.querySelector("#tm-minimap-selection");
        var actions = modal.querySelectorAll(".tm-minimap-actions [data-action]");
        if (selectionLabel) selectionLabel.textContent = translated("点击地图选择格子");
        Array.prototype.forEach.call(actions, function (action) { action.disabled = true; });
      }
    });
  }
  function closeMinimapModal(modal) {
    if (!modal) return;
    if (modal._miniMapRenderer) modal._miniMapRenderer.destroy();
    modal.remove();
    renderEventPanel();
  }
  function showMinimapEventInterpreterPicker(eventId) {
    var selection = context.minimapSelection;
    if (!selection) return;
    if (!selection.events.length) { toast(translated("该格没有事件")); return; }
    var candidates = selection.events;
    if (eventId != null) candidates = candidates.filter(function (entry) { return Number(entry.id) === Number(eventId); });
    if (eventId == null && candidates.length === 1) eventId = candidates[0].id;
    if (eventId == null) {
      var eventRows = candidates.map(function (entry) {
        var known = (context.eventAll || []).filter(function (value) { return Number(value.id) === Number(entry.id); })[0];
        return '<div class="tm-item"><span class="tm-name">#' + entry.id + ' ' + escapeHtml(known && known.name || "") + '</span>'
          + button("事件解释器", "minimap-pick-event", "", 'data-id="' + entry.id + '"') + '</div>';
      }).join("");
      var chooser = document.createElement("div");
      chooser.className = "tm-modal tm-map-cell-modal";
      chooser.innerHTML = '<div class="tm-modal-box tm-map-cell-modal-box"><div class="tm-modal-head"><strong>'
        + escapeHtml(translated("选择事件")) + '</strong>' + button("×", "close-cell-actions", "") + '</div>'
        + '<div class="tm-modal-body tm-map-event-picker">' + eventRows + '</div></div>';
      chooser.addEventListener("click", function (event) { if (event.target === chooser) chooser.remove(); });
      root.appendChild(chooser);
      return;
    }
    if (!candidates.length) return;
    var selectedId = Number(candidates[0].id);
    var detail;
    try { detail = mod.eventDetail(selectedId, selection.mapId); } catch (error) { toast(error.message || String(error), true); return; }
    if (!detail || !detail.pages || !detail.pages.length) { toast(translated("事件没有可显示的页面"), true); return; }
    context.eventId = selectedId;
    context.selectedMapId = selection.mapId;
    context.selectedEventDetail = detail;
    context.mapListExpanded = false;
    if (detail.pages.length === 1) return showEventInterpreterDialog(detail.pages[0].index);
    var pageRows = detail.pages.map(function (page) {
      return '<div class="tm-item"><span class="tm-name">' + escapeHtml(translated("事件页") + " " + (Number(page.index) + 1))
        + ' · ' + escapeHtml(translated("指令数") + " " + page.commandCount) + '</span>'
        + button("事件解释器", "minimap-open-event-page", "", 'data-id="' + selectedId + '" data-page="' + page.index + '"') + '</div>';
    }).join("");
    var pagePicker = document.createElement("div");
    pagePicker.className = "tm-modal tm-map-cell-modal";
    pagePicker.innerHTML = '<div class="tm-modal-box tm-map-cell-modal-box"><div class="tm-modal-head"><strong>'
      + escapeHtml(translated("事件解释器") + " · #" + detail.id + " " + (detail.name || "")) + '</strong>' + button("×", "close-cell-actions", "") + '</div>'
      + '<div class="tm-modal-body tm-map-event-picker">' + pageRows + '</div></div>';
    pagePicker.addEventListener("click", function (event) { if (event.target === pagePicker) pagePicker.remove(); });
    root.appendChild(pagePicker);
  }
  function eventCommandName(code) {
    var names = {
      0: "结束", 101: "显示文本", 102: "显示选项", 103: "数值输入", 104: "物品选择",
      105: "滚动文本", 108: "注释", 111: "条件分支", 112: "循环", 113: "中断循环",
      115: "事件处理结束", 117: "调用公共事件", 118: "标签", 119: "跳转标签", 121: "控制开关",
      122: "控制变量", 123: "控制独立开关", 124: "控制定时器", 125: "增减金钱", 126: "增减物品",
      127: "增减武器", 128: "增减护甲", 129: "更改队伍", 201: "传送玩家", 202: "设置事件位置",
      203: "设置地图滚动", 204: "更改地图设置", 205: "设置移动路线", 206: "移动路线结束",
      211: "显示动画", 212: "显示气泡图标", 213: "事件透明度", 221: "淡出画面", 222: "淡入画面",
      223: "更改画面色调", 224: "画面闪烁", 225: "画面震动", 230: "等待", 231: "显示图片",
      232: "移动图片", 235: "移除图片", 241: "播放 BGM", 245: "播放 BGS", 249: "播放 ME",
      250: "播放 SE", 251: "停止 SE", 301: "战斗处理", 302: "商店处理", 303: "名称输入",
      311: "增减 HP", 312: "增减 MP", 313: "更改状态", 314: "全体恢复", 315: "增减经验值",
      316: "增减等级", 317: "增减能力值", 318: "增减技能", 319: "更改装备", 320: "更改角色名称",
      321: "更改职业", 322: "更改角色图像", 323: "更改载具图像", 331: "增减敌人 HP", 332: "增减敌人 MP",
      333: "更改敌人状态", 334: "敌人全体恢复", 335: "敌人出现", 336: "敌人变身", 337: "显示战斗动画",
      339: "强制行动", 340: "中断战斗", 351: "打开菜单", 352: "打开存档画面", 353: "游戏结束",
      355: "脚本", 401: "文本内容", 402: "选项分支", 403: "取消分支", 404: "结束选项分支",
      405: "滚动文本内容", 408: "注释行", 412: "结束条件分支", 413: "重复循环", 601: "胜利时",
      602: "逃跑时", 603: "失败时", 604: "结束战斗分支", 655: "脚本行"
    };
    return names[Number(code)] || "事件指令";
  }
  function showEventInterpreterDialog(pageIndex) {
    var detail = context.selectedEventDetail;
    if (!detail) return;
    var page = (detail.pages || []).filter(function (entry) { return Number(entry.index) === Number(pageIndex); })[0];
    if (!page) return;
    var modal = document.createElement("div");
    modal.className = "tm-modal";
    modal.innerHTML = '<div class="tm-modal-box tm-interpreter-modal">'
      + '<div class="tm-modal-head"><strong>事件解释器</strong><span class="tm-interpreter-page">事件页 ' + (Number(page.index) + 1) + '</span>' + button("×", "close-interpreter", "") + '</div>'
      + '<div class="tm-modal-body tm-interpreter-list"><div class="tm-note">加载事件指令…</div></div>'
      + '<div class="tm-modal-hint">点击指令可展开查看参数</div></div>';
    modal.addEventListener("click", function (event) { if (event.target === modal) modal.remove(); });
    root.appendChild(modal);
    try {
      var commands = mod.eventInterpreter(detail.id, detail.mapId, page.index) || [];
      var commandHtml = commands.map(function (command) {
        var indent = Math.max(0, Math.min(8, Number(command.indent) || 0));
        var params = String(command.parameters || "");
        var preview = String(command.preview || "").trim();
        return '<details class="tm-interpreter-command" style="margin-left:' + (indent * 8) + 'px">'
          + '<summary><span class="tm-command-code">' + command.code + '</span><strong>' + eventCommandName(command.code) + '</strong>'
          + (preview ? '<span class="tm-command-preview" title="' + escapeHtml(preview) + '">' + escapeHtml(preview) + '</span>' : '')
          + '<span class="tm-command-index">#' + command.index + '</span></summary>'
          + '<pre>' + escapeHtml(params || "（无参数）") + '</pre></details>';
      }).join("") || '<div class="tm-note">此事件页没有指令</div>';
      var list = modal.querySelector(".tm-interpreter-list");
      if (list) list.innerHTML = commandHtml;
    } catch (error) {
      var list = modal.querySelector(".tm-interpreter-list");
      if (list) list.innerHTML = '<div class="tm-note">' + escapeHtml(String(error && error.message || error)) + '</div>';
    }
  }
  function showEventConditionsDialog(pageIndex) {
    var detail = context.selectedEventDetail;
    if (!detail) return;
    var page = (detail.pages || []).filter(function (entry) { return Number(entry.index) === Number(pageIndex); })[0];
    if (!page) return;
    var modal = document.createElement("div");
    modal.className = "tm-modal";
    modal.innerHTML = '<div class="tm-modal-box tm-conditions-modal">'
      + '<div class="tm-modal-head"><strong>' + escapeHtml(translated("事件条件")) + '</strong><span class="tm-interpreter-page">事件页 ' + (Number(page.index) + 1) + '</span>' + button("×", "close-conditions", "") + '</div>'
      + '<div class="tm-modal-body">' + eventConditionEntriesHtml(page) + '</div>'
      + '<div class="tm-modal-hint">' + escapeHtml(translated("点击开关或独立开关条件可单独切换")) + '</div></div>';
    modal.addEventListener("click", function (event) { if (event.target === modal) modal.remove(); });
    root.appendChild(modal);
  }
  function renderBattle(content) {
    var snapshot = mod.battleSnapshot();
    context.inBattle = !!snapshot.inBattle;
    var state = mod.getState();
    var supported = context.capabilities && context.capabilities.toggles;
    var toggles = battleFlags.filter(function (entry) {
      var capability = entry[0] === "godMode" ? "invincible" : entry[0] === "alwaysCrit" ? "crit" : entry[0];
      return !supported || supported.indexOf(capability) >= 0;
    }).map(function (entry) {
      return row(entry[1], '<input class="tm-switch" type="checkbox" data-action="flag" data-flag="' + entry[0] + '" ' + (state[entry[0]] ? "checked" : "") + '>');
    }).join("");
    if (!toggles) toggles = '<div class="tm-note">当前游戏没有可用的战斗开关</div>';
    var actionDisabled = snapshot.inBattle ? "" : "disabled";
    var actions = '<div class="tm-battle-actions">' +
      button("全体恢复", "recover-party", "primary") +
      button("消灭全部敌人", "kill-enemies", "danger", actionDisabled) +
      button("强制胜利", "victory", "primary", actionDisabled) +
      button("强制战败", "defeat", "danger", actionDisabled) +
      button("强制逃跑", "escape", "", actionDisabled) + '</div>';
    var supportedValues = context.capabilities && context.capabilities.values;
    var rewardFields = [];
    if (!supportedValues || supportedValues.indexOf("expMultiplier") >= 0) {
      rewardFields.push('<div class="tm-row"><label for="tm-exp-multiplier">经验倍率</label>' +
        textInput("tm-exp-multiplier", state.expMultiplier == null ? 1 : state.expMultiplier, "number", 'min="0" max="100" step="0.1"') +
        button("设置", "set-exp-multiplier", "primary") + '</div>');
    }
    if (!supportedValues || supportedValues.indexOf("dropMultiplier") >= 0) {
      rewardFields.push('<div class="tm-row"><label for="tm-drop-multiplier">掉落倍率</label>' +
        textInput("tm-drop-multiplier", state.dropMultiplier == null ? 1 : state.dropMultiplier, "number", 'min="0" max="100" step="0.1"') +
        button("设置", "set-drop-multiplier", "primary") + '</div>');
    }
    var rewards = rewardFields.length ? card("战斗奖励倍率", '<div class="tm-reward-multipliers">' + rewardFields.join("") + '</div>', "tm-battle-reward") : "";
    var enemies = snapshot.enemies.filter(function (enemy) { return !context.hideEmptyBattleEnemies || String(enemy.name || "").trim().length > 0; }).map(function (enemy) { return '<div class="tm-item"><span class="tm-name">' + escapeHtml(enemy.name) + ' HP ' + enemy.hp + '/' + enemy.mhp + '</span>' + textInput("tm-enemy-" + enemy.index, enemy.hp, "number", 'style="width:86px"') + button("设置", "set-enemy", "", 'data-index="' + enemy.index + '"') + '</div>'; }).join("");
    var actors = snapshot.actors.filter(function (actor) { return !context.hideEmptyBattleActors || String(actor.name || "").trim().length > 0; }).map(function (actor) { return '<div class="tm-item"><span class="tm-name">' + escapeHtml(actor.name) + '</span><span class="tm-badge">HP ' + actor.hp + '/' + actor.mhp + '</span></div>'; }).join("");
    if (!snapshot.inBattle) {
      enemies = '<div class="tm-note">当前不在战斗中</div>';
      actors = '<div class="tm-note">当前不在战斗中</div>';
    }
    if (snapshot.inBattle && !enemies) enemies = '<div class="tm-note">没有匹配项目</div>';
    if (snapshot.inBattle && !actors) actors = '<div class="tm-note">没有匹配项目</div>';
    content.innerHTML = '<div class="tm-battle-page">' +
      '<div class="tm-grid tm-battle-controls">' + card("战斗开关", toggles) + card("战斗操作", actions) + rewards + '</div>' +
      '<div class="tm-grid tm-battle-grid">' +
      card("我方", '<div class="tm-list">' + actors + '</div>', "", snapshot.inBattle ? hideEmptyToggle("toggle-hide-empty-battle-actors", context.hideEmptyBattleActors) : "") +
      card("敌方", '<div class="tm-list">' + enemies + '</div>', "", snapshot.inBattle ? hideEmptyToggle("toggle-hide-empty-battle-enemies", context.hideEmptyBattleEnemies) : "") + '</div>' +
      '</div>';
    ensureCapabilities();
  }
  function renderSave(content) {
    content.innerHTML = card("存档管理", '<div id="tm-save-list" class="tm-list"><div class="tm-note">读取中…</div></div>');
    run(function () { return mod.listSaveSlots(); }, function (slots) {
      root.querySelector("#tm-save-list").innerHTML = slots.map(function (slot) {
        return '<div class="tm-item"><span class="tm-name">' + escapeHtml(translated("存档 ")) + slot.id + (slot.exists ? ' · ' + escapeHtml(slot.playtime || slot.title || translated("已有数据")) : ' · ' + escapeHtml(translated("空"))) + '</span>' + button(translated(slot.exists ? "覆盖" : "保存"), "save", "primary", 'data-id="' + slot.id + '"') + (slot.exists ? button(translated("读取"), "load", "", 'data-id="' + slot.id + '"') + button(translated("删除"), "delete-save", "danger", 'data-id="' + slot.id + '"') : '') + '</div>';
      }).join("");
    });
  }
  function renderAbout(content) {
    content.innerHTML = card("Tyranor 修改器", '<p>版本 ' + escapeHtml(mod.version) + '</p><p>修改器只操作当前 RGSS 游戏的运行时对象。部分深度定制脚本可能覆盖标准引擎 API；遇到异常时可在单游戏设置中关闭修改器。</p><p>事件加速和对话快进可能跳过演出或等待，建议先保存游戏。</p>');
  }
  function inputBridge() {
    return window.TyranorInputNative || null;
  }
  function isInBattleNow() {
    try {
      context.inBattle = !!mod.battleSnapshot().inBattle;
    } catch (error) {
      console.warn("RGSS battle state read failed", error);
    }
    return context.inBattle;
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
    if (action === "quick-section") { context.quickSection = node.dataset.section; return renderQuick(root.querySelector(".tm-content"), false); }
    if (action === "flag") {
      var flagOn = node.type === "checkbox" ? node.checked : !mod.getState()[node.dataset.flag];
      return run(function () { return mod.setFlag(node.dataset.flag, flagOn); }, function () { toast("设置已保存"); renderEventPanel(); })
        .then(function (result) { if (result === undefined && node.type === "checkbox") node.checked = !flagOn; });
    }
    if (action === "set-speed") return run(function () { return mod.setSpeed(inputValue("tm-speed")); }, function () { toast("移动速度已设置"); renderQuick(root.querySelector(".tm-content"), false); });
    if (action === "set-game-speed") return run(function () { return mod.setGameSpeed(inputValue("tm-game-speed")); }, function () { toast("游戏倍速已设置"); renderQuick(root.querySelector(".tm-content"), false); });
    if (action === "set-exp-multiplier") return run(function () { return mod.setRewardMultiplier("expMultiplier", inputValue("tm-exp-multiplier")); }, function () { toast("经验倍率已设置"); renderBattle(root.querySelector(".tm-content")); });
    if (action === "set-drop-multiplier") return run(function () { return mod.setRewardMultiplier("dropMultiplier", inputValue("tm-drop-multiplier")); }, function () { toast("掉落倍率已设置"); renderBattle(root.querySelector(".tm-content")); });
    if (action === "max-gold") return run(function () { mod.setGold(99999999); }, function () { toast("金币已拉满"); render(); });
    if (action === "lock-item") {
      var itemLocked = node.checked;
      return run(function () { return mod.setItemLock(context.itemKind, node.dataset.id, itemLocked); }, function () { toast(itemLocked ? "物品已锁定" : "已取消锁定"); })
        .then(function (result) { if (result === undefined) node.checked = !itemLocked; });
    }
    if (action === "recover-party") return run(mod.recoverParty, function () { toast("队伍已恢复"); render(); });
    if (action === "toggle-item-kind") {
      context.itemKindExpanded = !context.itemKindExpanded;
      var picker = root.querySelector(".tm-item-kind-picker");
      var trigger = picker && picker.querySelector(".tm-system-kind-trigger");
      var menu = picker && picker.querySelector(".tm-system-kind-menu");
      if (trigger) trigger.setAttribute("aria-expanded", context.itemKindExpanded ? "true" : "false");
      if (menu) menu.remove();
      if (context.itemKindExpanded && picker) {
        menu = document.createElement("div");
        menu.className = "tm-system-kind-menu";
        menu.setAttribute("role", "listbox");
        menu.innerHTML = itemKindOptionsMarkup();
        picker.appendChild(menu);
      }
      return;
    }
    if (action === "select-item-kind") {
      context.itemKind = node.dataset.kind === "weapon" || node.dataset.kind === "armor" ? node.dataset.kind : "item";
      context.itemKindExpanded = false;
      context.selectedItemId = null;
      return renderItems(root.querySelector(".tm-content"));
    }
    if (action === "render-items") return loadItems();
    if (action === "select-item") {
      context.selectedItemId = Number(node.dataset.id);
      root.querySelectorAll("#tm-item-list .tm-item-lock-row").forEach(function (itemRow) {
        var selectButton = itemRow.querySelector("[data-action='select-item']");
        var selected = selectButton && Number(selectButton.dataset.id) === context.selectedItemId;
        itemRow.classList.toggle("selected", selected);
        if (selectButton) selectButton.setAttribute("aria-pressed", selected ? "true" : "false");
      });
      return renderItemDetail();
    }
    if (action === "set-item") return run(function () { return mod.setItemCount(context.itemKind, node.dataset.id, inputValue("tm-detail-count")); }, function () { toast("数量已修改"); loadItems(); });
    if (action === "actor-select") {
      context.actorId = Number(node.dataset.id || node.value);
      context.skillQuery = "";
      context.stateQuery = "";
      context.equipSlot = null;
      context.equipKind = "";
      renderActorRoster();
      return renderActorBody(context.actorEntries || []);
    }
    if (action === "actor-party") {
      var membership = node.checked;
      return run(function () { return mod.setActorParty(Number(node.dataset.id), membership); }, function () {
        var list = root.querySelector("#tm-actor-list");
        var scrollTop = list ? list.scrollTop : 0;
        context.actorEntries = mod.listActors();
        renderActorRoster();
        list = root.querySelector("#tm-actor-list");
        if (list) list.scrollTop = scrollTop;
        renderActorBody(context.actorEntries);
        toast("队伍成员已更新");
      }).then(function (result) { if (result === undefined) node.checked = !membership; });
    }
    if (action === "actor-section") { context.actorSection = node.dataset.section; return renderActorSection(); }
    if (action === "toggle-hide-empty-skills") { context.hideEmptySkills = !!node.checked; return renderActorCollectionList("skills"); }
    if (action === "toggle-hide-empty-system") { context.hideEmptySystemEntries = !!node.checked; return loadSystem(); }
    if (action === "toggle-hide-empty-equips") { context.hideEmptyEquipCandidates = !!node.checked; return renderActorSection(); }
    if (action === "toggle-hide-empty-battle-enemies") { context.hideEmptyBattleEnemies = !!node.checked; return renderBattle(root.querySelector(".tm-content")); }
    if (action === "toggle-hide-empty-battle-actors") { context.hideEmptyBattleActors = !!node.checked; return renderBattle(root.querySelector(".tm-content")); }
    if (action === "pick-equip-slot") {
      var equipScroll = pendingEquipScroll || readEquipScroll();
      pendingEquipScroll = null;
      context.equipSlot = Number(node.dataset.id);
      var pickedSlot = (mod.actorEquips(context.actorId) || []).filter(function (entry) { return Number(entry.slot) === context.equipSlot; })[0];
      context.equipKind = pickedSlot && pickedSlot.type === "weapon" ? "weapon" : "armor";
      renderActorSection();
      restoreEquipScroll(equipScroll);
      if (window.requestAnimationFrame) window.requestAnimationFrame(function () { restoreEquipScroll(equipScroll); });
      return;
    }
    if (action === "recover-actor") return run(function () { mod.recoverActor(context.actorId); }, function () { toast("角色已恢复"); renderActorBody(); });
    if (action === "toggle-actor-class") {
      context.actorClassExpanded = !context.actorClassExpanded;
      return renderActorSection();
    }
    if (action === "actor-class" || action === "select-actor-class") {
      var classId = Number(action === "actor-class" ? node.value : node.dataset.id);
      if (!(classId > 0)) return renderActorSection();
      context.actorClassExpanded = false;
      return run(function () { return mod.setActorClass(context.actorId, classId); }, function () {
        toast(translated("职业已更换"));
        renderActorBody();
      }).then(function (result) {
        if (result === undefined && root) renderActorSection();
      });
    }
    if (action === "set-stat") return run(function () { mod.setActorStat(context.actorId, node.dataset.key, inputValue("tm-stat-" + node.dataset.key)); }, function () { toast("属性已修改"); renderActorBody(); });
    if (action === "learn-skill" || action === "forget-skill") return run(function () { var skillId = Number(node.dataset.id); return action === "learn-skill" ? mod.learnSkill(context.actorId, skillId) : mod.forgetSkill(context.actorId, skillId); }, function (entries) { refreshActorCollection("skills", entries); });
    if (action === "add-state" || action === "remove-state") return run(function () { var stateId = Number(node.dataset.id); return action === "add-state" ? mod.addState(context.actorId, stateId) : mod.removeState(context.actorId, stateId); }, function (entries) { refreshActorCollection("states", entries); });
    if (action === "equip-candidate" || action === "unequip-equip") {
      var slotIndex = action === "unequip-equip" ? Number(node.dataset.id) : Number(context.equipSlot);
      var targetSlot = (mod.actorEquips(context.actorId) || []).filter(function (entry) { return Number(entry.slot) === slotIndex; })[0];
      if (!targetSlot) { toast(translated("当前角色没有此类槽位"), true); return; }
      var itemId = action === "unequip-equip" ? 0 : Number(node.dataset.id);
      if (action === "unequip-equip" && !(Number(targetSlot.id) > 0)) return;
      if (action === "equip-candidate" && !(itemId > 0)) return;
      var equipKind = targetSlot.type === "weapon" ? "weapon" : "armor";
      return run(function () { return mod.changeEquip(context.actorId, slotIndex, equipKind, itemId); }, function () {
        toast(translated(action === "unequip-equip" ? "装备已卸下" : "装备已更换"));
        renderActorBody();
      });
    }
    if (action === "toggle-system-kind") {
      context.systemKindExpanded = !context.systemKindExpanded;
      return renderSystem(root.querySelector(".tm-content"));
    }
    if (action === "select-system-kind") {
      context.systemKind = node.dataset.kind === "variable" ? "variable" : "switch";
      context.systemKindExpanded = false;
      return renderSystem(root.querySelector(".tm-content"));
    }
    if (action === "render-system") return loadSystem();
    if (action === "set-system-switch") {
      var nextSwitchValue = !!node.checked;
      var previousSwitchValue = node.dataset.currentValue === "1";
      var switchStateLabel = node.parentNode.querySelector(".tm-system-switch-state");
      return run(function () { return mod.setSwitch(node.dataset.id, nextSwitchValue); }, function () {
        node.dataset.currentValue = nextSwitchValue ? "1" : "0";
        if (switchStateLabel) switchStateLabel.textContent = translated(nextSwitchValue ? "开" : "关");
      }).then(function (result) {
        if (result === undefined) {
          node.checked = previousSwitchValue;
          if (switchStateLabel) switchStateLabel.textContent = translated(previousSwitchValue ? "开" : "关");
        }
      });
    }
    if (action === "set-system") return run(function () { var id = node.dataset.id; if (context.systemKind === "switch") mod.setSwitch(id, checked("tm-system-" + id)); else { var raw = inputValue("tm-system-" + id); var value; try { value = JSON.parse(raw); } catch (_) { value = raw; } mod.setVariable(id, value); } }, function () { toast("系统值已修改"); loadSystem(); });
    if (action === "lock-system") {
      var systemLocked = node.checked;
      return run(function () { return mod.setSystemLock(context.systemKind, node.dataset.id, systemLocked); }, function () { toast(systemLocked ? "系统值已锁定" : "已取消锁定"); })
        .then(function (result) { if (result === undefined) node.checked = !systemLocked; });
    }
    if (action === "toggle-map-list") {
      context.mapListExpanded = !context.mapListExpanded;
      var browseQuery = root.querySelector("#tm-browse-query");
      if (browseQuery) browseQuery.value = "";
      return loadBrowse();
    }
    if (action === "render-browse") return loadBrowse();
    if (action === "pick-map") {
      var mapId = Number(node.dataset.id) || 0;
      var selectedMap = (context.mapEntries || []).filter(function (entry) { return Number(entry.id) === mapId; })[0];
      fillTeleport(mapId, coordinateOrZero(selectedMap && selectedMap.currentX), coordinateOrZero(selectedMap && selectedMap.currentY));
      context.selectedMapId = mapId;
      context.eventId = 0;
      context.mapListExpanded = false;
      var browseQuery = root.querySelector("#tm-browse-query");
      if (browseQuery) browseQuery.value = "";
      loadEventsForMap(mapId);
      loadBrowse();
      renderEventPanel();
      return;
    }
    if (action === "pick-event") {
      context.eventId = Number(node.dataset.id);
      var ev = (context.eventAll || []).filter(function (e2) { return String(e2.id) === node.dataset.id; })[0];
      if (ev) fillTeleport(ev.mapId, coordinateOrZero(ev.x), coordinateOrZero(ev.y));
      renderEventList(); renderEventPanel(); return;
    }
    if (action === "map-selfswitches") {
      var enabled = node.dataset.enabled === "1";
      return run(function () { return mod.setMapEventSelfSwitches(context.selectedMapId, enabled); }, function () {
        loadEventsForMap(context.selectedMapId);
        loadBrowse();
        renderEventPanel();
        toast(enabled ? "所选地图事件开关已全开" : "所选地图事件开关已全关");
      });
    }
    if (action === "toggle-event-condition") {
      var conditionEnabled = node.dataset.enabled === "1";
      var conditionId = node.dataset.id;
      var conditionType = node.dataset.conditionType;
      var conditionPageIndex = node.dataset.page;
      return run(function () {
        if (conditionType === "self_switch") return mod.setEventSelfSwitch(context.eventId, conditionId, conditionEnabled, context.selectedMapId);
        return mod.setSwitch(conditionId, conditionEnabled);
      }, function () {
        renderEventPanel();
        var conditionsModal = root.querySelector(".tm-conditions-modal");
        if (conditionsModal) {
          var conditionsOverlay = conditionsModal.parentNode;
          if (conditionsOverlay && conditionsOverlay.parentNode) conditionsOverlay.parentNode.removeChild(conditionsOverlay);
          showEventConditionsDialog(conditionPageIndex);
        }
        toast("事件条件开关已更新");
      });
    }
    if (action === "show-minimap") return showMinimapDialog();
    if (action === "close-minimap") { var minimap = node.closest(".tm-minimap-modal"); if (minimap) closeMinimapModal(minimap); return; }
    if (action === "close-cell-actions") { var cellDialog = node.closest(".tm-map-cell-modal"); if (cellDialog) cellDialog.remove(); return; }
    if (action === "minimap-cell-teleport") {
      var selection = context.minimapSelection;
      if (!selection) return;
      var minimap = node.closest(".tm-minimap-modal");
      return run(function () { return mod.teleport(selection.mapId, selection.x, selection.y, 2, 0); }, function () {
        toast("已预约传送");
        window.setTimeout(function () {
          if (!minimap || !minimap.parentNode || !minimap._miniMapRenderer) return;
          try {
            var updated = mod.minimapData();
            if (!updated || !minimap._miniMapRenderer.updateData(updated)) return;
            var updatedMapId = Number(updated.mapId) || selection.mapId;
            if (updatedMapId !== selection.mapId) {
              var viewport = minimap.querySelector(".tm-minimap-viewport");
              if (viewport) { viewport.scrollLeft = 0; viewport.scrollTop = 0; }
            }
            var heading = minimap.querySelector(".tm-modal-head > strong");
            if (heading) heading.textContent = translated("迷你地图 #") + updatedMapId + " · " + updated.width + "×" + updated.height;
            if (context.minimapSelection && context.minimapSelection.x === selection.x && context.minimapSelection.y === selection.y) {
              if (selection.x < Number(updated.width) && selection.y < Number(updated.height)) {
                context.minimapSelection = {
                  mapId: updatedMapId,
                  x: selection.x,
                  y: selection.y,
                  events: (updated.events || []).filter(function (entry) { return Number(entry.x) === selection.x && Number(entry.y) === selection.y; })
                };
              } else context.minimapSelection = null;
            }
            context.currentMapId = updatedMapId;
          } catch (_) {}
        }, 350);
      });
    }
    if (action === "minimap-cell-event-interpreter") {
      return showMinimapEventInterpreterPicker();
    }
    if (action === "minimap-pick-event") {
      var eventChooser = node.closest(".tm-map-cell-modal"); if (eventChooser) eventChooser.remove();
      return showMinimapEventInterpreterPicker(node.dataset.id);
    }
    if (action === "minimap-open-event-page") {
      var pageChooser = node.closest(".tm-map-cell-modal"); if (pageChooser) pageChooser.remove();
      return showEventInterpreterDialog(node.dataset.page);
    }
    if (action === "show-event-conditions") return showEventConditionsDialog(node.dataset.page);
    if (action === "close-conditions") { var conditions = root.querySelector(".tm-conditions-modal"); var overlay = conditions && conditions.parentNode; if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay); return; }
    if (action === "show-event-interpreter") return showEventInterpreterDialog(node.dataset.page);
    if (action === "close-interpreter") { var interpreter = node.closest(".tm-modal"); if (interpreter) interpreter.remove(); return; }
    if (action === "teleport") return run(function () { return mod.teleport(inputValue("tm-map-id"), inputValue("tm-map-x"), inputValue("tm-map-y"), 2, 0); }, function () { toast("已预约传送"); close(); });
    if (action === "set-enemy") { if (!isInBattleNow()) return; return run(function () { mod.setEnemyHp(node.dataset.index, inputValue("tm-enemy-" + node.dataset.index)); }, render); }
    if (action === "kill-enemies") { if (!isInBattleNow()) return; return run(mod.killAllEnemies, render); }
    if (action === "victory") { if (!isInBattleNow()) return; return run(mod.forceVictory, close); }
    if (action === "defeat") { if (!isInBattleNow()) return; return run(mod.forceDefeat, close); }
    if (action === "escape") { if (!isInBattleNow()) return; return run(mod.forceEscape, close); }
    if (action === "save") return run(function () { return mod.saveGame(node.dataset.id); }, function () { toast("保存完成"); renderSave(root.querySelector(".tm-content")); });
    if (action === "load") return run(function () { return mod.loadGame(node.dataset.id); }, function () { toast("读取完成"); close(); });
    if (action === "delete-save") return run(function () { return mod.deleteSave(node.dataset.id); }, function () { toast("存档已删除"); }).then(function () {
      var content = root && root.querySelector(".tm-content");
      if (content) renderSave(content);
    });
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
    root.innerHTML = '<div class="tm-panel" role="dialog" aria-modal="true"><div class="tm-head"><strong>RPG Maker RGSS 修改器</strong><button class="tm-refresh" data-action="render" title="刷新面板" aria-label="刷新面板">↻</button><button class="tm-close" data-action="close" aria-label="关闭">×</button></div><div class="tm-tabs">' + tabs.map(function (tab) { return '<button class="tm-tab" data-tab="' + tab[0] + '">' + tab[1] + '</button>'; }).join("") + '</div><main class="tm-content"></main></div>';
    document.documentElement.appendChild(root);
    root.addEventListener("pointerdown", function (event) {
      pendingEquipScroll = event.target.closest("[data-action='pick-equip-slot']") ? readEquipScroll() : null;
    }, true);
    root.addEventListener("click", function (event) {
      event.stopPropagation();
      if (context.mapListExpanded) {
        var target = event.target;
        var insidePicker = target.closest(".tm-map-picker, #tm-map-list");
        var insideMapSearch = target.closest("#tm-browse-query, [data-action='render-browse']");
        var pickingMap = target.closest("[data-action='pick-map']");
        if (!insidePicker && !insideMapSearch && !pickingMap) {
          context.mapListExpanded = false;
          var browseQuery = root.querySelector("#tm-browse-query");
          if (browseQuery) browseQuery.value = "";
          loadBrowse();
        }
      }
      if (context.systemKindExpanded && !event.target.closest(".tm-system-kind-picker")) {
        context.systemKindExpanded = false;
        var systemMenu = root.querySelector(".tm-system-kind-menu");
        var systemTrigger = root.querySelector(".tm-system-kind-trigger");
        if (systemMenu) systemMenu.remove();
        if (systemTrigger) systemTrigger.setAttribute("aria-expanded", "false");
      }
      if (context.itemKindExpanded && !event.target.closest(".tm-item-kind-picker")) {
        context.itemKindExpanded = false;
        var itemMenu = root.querySelector(".tm-item-kind-picker .tm-system-kind-menu");
        var itemTrigger = root.querySelector(".tm-item-kind-picker .tm-system-kind-trigger");
        if (itemMenu) itemMenu.remove();
        if (itemTrigger) itemTrigger.setAttribute("aria-expanded", "false");
      }
      if (context.actorClassExpanded && !event.target.closest(".tm-actor-class-picker")) {
        context.actorClassExpanded = false;
        var classMenu = root.querySelector(".tm-actor-class-menu");
        var classTrigger = root.querySelector(".tm-actor-class-picker .tm-system-kind-trigger");
        if (classMenu) classMenu.remove();
        if (classTrigger) classTrigger.setAttribute("aria-expanded", "false");
      }
      var tab = event.target.closest("[data-tab]"); if (tab) { activeTab = tab.dataset.tab; render(); return; }
      var action = event.target.closest("[data-action]");
      if (action && action.type === "checkbox") return;
      if (action && action.dataset.action === "select-item" && event.target.closest(".tm-lock-cell")) return;
      if (action) handleAction(action.dataset.action, action);
    });
    root.addEventListener("change", function (event) {
      if (event.target.classList && event.target.classList.contains("tm-system-switch-input")) {
        handleAction("set-system-switch", event.target);
        event.stopPropagation();
        return;
      }
      if (event.target.id === "tm-gold") applyGoldInput(event.target);
      else { var action = event.target.closest("[data-action]"); if (action) handleAction(action.dataset.action, action); }
      event.stopPropagation();
    });
    root.addEventListener("keydown", function (event) {
      if (event.target && event.target.id === "tm-gold" && event.key === "Enter") {
        event.preventDefault();
        applyGoldInput(event.target);
        event.target.blur();
      } else if (event.target && event.target.classList && event.target.classList.contains("tm-system-value") && event.key === "Enter") {
        event.preventDefault();
        event.target.blur();
      }
    });
    root.addEventListener("input", function (event) {
      if (event.target.id === "tm-gold") fitGoldInput(event.target);
      else if (event.target.id === "tm-actor-query") { context.actorQuery = event.target.value; renderActorRoster(); }
      else if (event.target.id === "tm-skill-query") { context.skillQuery = event.target.value; renderActorCollectionList("skills"); }
      else if (event.target.id === "tm-state-query") { context.stateQuery = event.target.value; renderActorCollectionList("states"); }
      else if (event.target.id === "tm-system-query") loadSystem();
    });
    ["touchstart","touchmove","touchend","pointerdown","pointerup","mousedown","mouseup","keydown","keyup","wheel"].forEach(function (name) { root.addEventListener(name, block); });
    render();
  }
  function close(restoreGameFocus) {
    if (!root) return false;
    root.querySelectorAll(".tm-minimap-modal").forEach(function (modal) {
      if (modal._miniMapRenderer) modal._miniMapRenderer.destroy();
    });
    root.remove(); root = null;
    try { if (window.TyranorCheatNative) window.TyranorCheatNative.onPanelClosed(restoreGameFocus !== false); } catch (_) {}
    return true;
  }
  function closeForEngineSwitch() {
    if (!root) return false;
    root.querySelectorAll(".tm-minimap-modal").forEach(function (modal) {
      if (modal._miniMapRenderer) modal._miniMapRenderer.destroy();
    });
    root.remove(); root = null;
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
      window.TyranorModUI.toggle();
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
  window.TyranorModUI = { open: open, close: close, closeForEngineSwitch: closeForEngineSwitch, toggle: toggle, isOpen: isOpen };
})();
