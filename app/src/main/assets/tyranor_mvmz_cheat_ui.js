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
  var context = { actorId: 0, actorEntries: null, actorSection: "stats", actorQuery: "", actorSkillEntries: [], actorStateEntries: [], skillQuery: "", stateQuery: "", hideEmptySkills: false, hideEmptySystemEntries: false, hideEmptyEquipCandidates: false, hideEmptyBattleEnemies: false, hideEmptyBattleActors: false, equipSlot: null, equipKind: "", equipItemId: null, quickSection: "角色与资源", inBattle: false, itemKind: "item", systemKind: "switch", mapListExpanded: false, currentMapId: 0, selectedMapId: 0, eventId: 0, selectedEventDetail: null, mapEntries: [], capabilities: null, capabilitiesLoading: false };
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
        if (loaded && loaded.engine !== "unknown" && Array.isArray(loaded.toggles)) context.capabilities = loaded;
      } catch (error) {
        console.warn("RGSS cheat capability read failed", error);
      }
      context.capabilitiesLoading = false;
      if (!context.capabilities || !root) return;
      if (activeTab === "quick") renderQuick(root.querySelector(".tm-content"), false);
      else if (activeTab === "battle") renderBattle(root.querySelector(".tm-content"));
    }, 0);
  }
  function renderItems(content) {
    var kinds = '<select class="tm-select" id="tm-item-kind" data-action="item-kind"><option value="item">物品</option><option value="weapon">武器</option><option value="armor">护甲</option></select>';
    var tools = '<div class="tm-row">' + kinds + textInput("tm-item-query", "", "search", 'placeholder="搜索名称"') + button("搜索", "render-items", "primary") + '</div>';
    var header = '<div class="tm-lock-list-header tm-item-lock-header"><span>锁定</span><span>物品</span><span>数量</span><span></span></div>';
    content.innerHTML = card("物品数据库", tools + header + '<div id="tm-item-list" class="tm-list"><div class="tm-note">加载中…</div></div>', "tm-fill-card");
    root.querySelector("#tm-item-kind").value = context.itemKind;
    loadItems();
  }
  function loadItems() {
    context.itemKind = inputValue("tm-item-kind") || context.itemKind;
    var result = mod.listDatabase(context.itemKind, inputValue("tm-item-query"), 0, 200);
    var list = root.querySelector("#tm-item-list");
    list.innerHTML = result.values.length ? result.values.map(function (item) {
      return '<div class="tm-item tm-lock-row tm-item-lock-row"><label class="tm-lock-cell"><input class="tm-lock-checkbox" type="checkbox" data-action="lock-item" data-id="' + escapeHtml(item.id) + '" ' + (item.locked ? "checked" : "") + '></label><span class="tm-name" title="' + escapeHtml(item.description) + '">#' + item.id + ' ' + escapeHtml(item.name) + '</span>' +
        textInput("tm-count-" + item.id, item.count, "number", 'min="0"') + button("应用", "set-item", "", 'data-id="' + item.id + '"') + '</div>';
    }).join("") : '<div class="tm-note">没有匹配项目</div>';
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
  function renderActorSection() {
    var actor = (context.actorEntries || []).filter(function (a) { return a.id === context.actorId; })[0];
    var section = root.querySelector("#tm-actor-section");
    if (!actor || !section) return;
    root.querySelectorAll(".tm-segmented-tab[data-action='actor-section']").forEach(function (tab) {
      tab.classList.toggle("active", tab.dataset.section === context.actorSection);
    });
    if (context.actorSection === "stats") {
      var stats = ["level","hp","mp","mhp","mmp","atk","def","mat","mdf","agi","luk"].map(function (key) {
      return '<div class="tm-item"><span class="tm-name">' + key.toUpperCase() + '</span>' + textInput("tm-stat-" + key, actor[key], "number", 'style="width:94px"') + button("设置", "set-stat", "", 'data-key="' + key + '"') + '</div>';
      }).join("");
      section.innerHTML = card("属性", '<div class="tm-list">' + stats + '</div>');
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
      section.innerHTML = card("装备", '<div class="tm-note">当前角色没有可修改的装备槽位</div>');
      return;
    }
    if (context.equipKind !== "weapon" && context.equipKind !== "armor") {
      var initialSlot = equips.filter(function (entry) { return Number(entry.slot) === Number(context.equipSlot); })[0] || equips[0];
      context.equipKind = initialSlot.type === "weapon" ? "weapon" : "armor";
    }
    var matchingSlots = equips.filter(function (entry) { return entry.type === context.equipKind; });
    var slot = matchingSlots.filter(function (entry) { return Number(entry.slot) === Number(context.equipSlot); })[0] || matchingSlots[0] || null;
    context.equipSlot = slot ? Number(slot.slot) : null;
    var candidates = slot ? (mod.equipCandidates(actor.id, Number(slot.slot)) || []) : [];
    var slotOptions = matchingSlots.map(function (entry) {
      var typeName = entry.slotName || translated(entry.type === "weapon" ? "武器" : "护甲");
      var itemName = entry.name && entry.name !== "None" ? entry.name : translated("无");
      var label = translated("槽位" + entry.slot + "：") + typeName + " · " + itemName;
      return '<option value="' + Number(entry.slot) + '" ' + (slot && Number(entry.slot) === Number(slot.slot) ? "selected" : "") + '>' + escapeHtml(label) + '</option>';
    }).join("") || '<option value="" selected disabled>' + escapeHtml(translated("当前角色没有此类槽位")) + '</option>';
    var kindOptions = '<option value="weapon" ' + (context.equipKind === "weapon" ? "selected" : "") + '>' + escapeHtml(translated("武器")) + '</option>' +
      '<option value="armor" ' + (context.equipKind === "armor" ? "selected" : "") + '>' + escapeHtml(translated("护甲")) + '</option>';
    var currentId = slot ? Number(slot.id) || 0 : 0;
    var currentKind = slot ? slot.type : context.equipKind;
    var currentRawName = slot && slot.name && slot.name !== "None" ? String(slot.name) : "";
    var currentName = slot && slot.name && slot.name !== "None" ? slot.name : translated("无");
    if (context.equipItemId == null) context.equipItemId = currentId;
    var available = candidates.filter(function (entry) {
      return entry.kind === context.equipKind && Number(entry.id) > 0 &&
        (!context.hideEmptyEquipCandidates || String(entry.name || "").trim().length > 0);
    });
    var currentCandidateExists = candidates.some(function (entry) { return entry.kind === currentKind && Number(entry.id) === currentId; });
    if (slot && currentId > 0 && currentKind === context.equipKind && !currentCandidateExists &&
      (!context.hideEmptyEquipCandidates || currentRawName.trim().length > 0)) {
      available.unshift({ kind: currentKind, id: currentId, name: currentName });
    }
    var currentRows = equips.map(function (entry) {
      var entryType = entry.type === "weapon" ? "武器" : "护甲";
      var entrySlotName = entry.slotName || translated(entryType);
      var itemName = entry.name && entry.name !== "None" ? entry.name : translated("无");
      var selected = slot && Number(entry.slot) === Number(slot.slot);
      return '<button type="button" class="tm-item tm-equip-slot-option' + (selected ? ' selected' : '') + '" data-action="pick-equip-slot" data-id="' + Number(entry.slot) + '"><span class="tm-equip-slot-name">' + escapeHtml(translated("槽位" + entry.slot + "：") + entrySlotName) + '</span><span class="tm-equip-slot-item">' + escapeHtml(itemName) + '</span></button>';
    }).join("");
    var itemRows = available.map(function (entry) {
      var equipped = Number(entry.id) === currentId && currentKind === context.equipKind;
      return '<button type="button" class="tm-item tm-equip-option" data-action="pick-equip" data-id="' + Number(entry.id) + '"><span class="tm-equip-option-name">#' + Number(entry.id) + ' ' + escapeHtml(entry.name) + '</span>' +
        (equipped ? '<span class="tm-badge">' + escapeHtml(translated("当前装备")) + '</span>' : '') + '</button>';
    }).join("") || '<div class="tm-note">' + escapeHtml(translated(slot ? "当前槽位没有可选装备" : "当前角色没有此类槽位")) + '</div>';
    var equipControls = '<div class="tm-equip-controls">' +
      '<select id="tm-equip-kind" class="tm-select" aria-label="' + escapeHtml(translated("装备类型")) + '">' + kindOptions + '</select>' +
      '<select id="tm-equip-slot" class="tm-select" aria-label="' + escapeHtml(translated("装备槽位")) + '">' + slotOptions + '</select>' +
      textInput("tm-equip-id", context.equipItemId, "number", 'placeholder="装备 ID"') +
      button("更换", "change-equip", slot ? "primary" : "", slot ? "" : "disabled") + '</div>';
    section.innerHTML = '<div class="tm-equip-page">' + card("装备", equipControls, "tm-equip-control-card") +
      '<div class="tm-equip-columns">' +
      card("当前装备", '<div class="tm-list tm-equip-slots">' + currentRows + '</div>', "tm-equip-data-card") +
      card(context.equipKind === "weapon" ? "可选武器" : "可选护甲", '<small class="tm-equip-hint">点击条目填入装备编号</small><div class="tm-list tm-equip-candidates">' + itemRows + '</div>', "tm-equip-data-card", hideEmptyToggle("toggle-hide-empty-equips", context.hideEmptyEquipCandidates)) +
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
    var kind = '<select class="tm-select" id="tm-system-kind" data-action="system-kind"><option value="switch">开关</option><option value="variable">变量</option></select>';
    var header = '<div class="tm-lock-list-header tm-system-lock-header"><span>锁定</span><span>名称</span><span>当前值</span><span></span></div>';
    content.innerHTML = card("开关与变量", '<div class="tm-row">' + kind + textInput("tm-system-query", "", "search", 'placeholder="名称或 ID"') + button("搜索", "render-system", "primary") + '</div>' + header + '<div id="tm-system-list" class="tm-list"></div>', "tm-fill-card", hideEmptyToggle("toggle-hide-empty-system", context.hideEmptySystemEntries));
    root.querySelector("#tm-system-kind").value = context.systemKind; loadSystem();
  }
  function loadSystem() {
    context.systemKind = inputValue("tm-system-kind") || context.systemKind;
    var includeUnnamed = !context.hideEmptySystemEntries;
    var result = context.systemKind === "switch" ? mod.listSwitches(inputValue("tm-system-query"), 0, 200, includeUnnamed) : mod.listVariables(inputValue("tm-system-query"), 0, 200, includeUnnamed);
    root.querySelector("#tm-system-list").innerHTML = result.values.filter(function (item) {
      return !context.hideEmptySystemEntries || item.named !== false;
    }).map(function (item) {
      var name = String(item.name || (context.systemKind === "switch" ? "开关 " : "变量 ") + item.id);
      var control = context.systemKind === "switch" ?
        '<label class="tm-system-switch"><span class="tm-system-switch-state">' + (item.value ? "设为开" : "设为关") + '</span><input class="tm-switch tm-system-switch-input" id="tm-system-' + item.id + '" type="checkbox" ' + (item.value ? "checked" : "") + '></label>' :
        '<input class="tm-input tm-system-value" id="tm-system-' + item.id + '" type="text" value="' + escapeHtml(typeof item.value === "object" ? JSON.stringify(item.value) : item.value) + '" aria-label="' + escapeHtml(name) + '">';
      return '<div class="tm-item tm-lock-row tm-system-lock-row"><label class="tm-lock-cell"><input class="tm-lock-checkbox" type="checkbox" data-action="lock-system" data-kind="' + context.systemKind + '" data-id="' + item.id + '" ' + (item.locked ? "checked" : "") + '></label><span class="tm-name" title="#' + item.id + ' ' + escapeHtml(name) + '">#' + item.id + ' ' + escapeHtml(name) + '</span>' + control + button("设置", "set-system", "", 'data-id="' + item.id + '"') + '</div>';
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
      + '<div class="tm-map-action-buttons">' + button("迷你地图", "show-minimap") + button("独立开关全开", "map-selfswitches", "", 'data-enabled="1"') + button("独立开关全关", "map-selfswitches", "", 'data-enabled="0"') + '</div>';
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
        + '<div class="tm-event-page-actions">' + button(translated("条件") + " (" + (page.conditions || []).length + ")", "show-event-conditions", "", 'data-page="' + page.index + '"') + button("事件解释器", "show-event-interpreter", "", 'data-page="' + page.index + '"') + '</div></div>'
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
      context.selectedMapId = Number(data.mapId) || context.currentMapId;
      context.mapListExpanded = false;
      var browseQuery = root.querySelector("#tm-browse-query");
      if (browseQuery) browseQuery.value = "";
      loadEventsForMap(context.selectedMapId);
      var selectedEvent = (context.eventAll || []).filter(function (entry) { return Number(entry.id) === Number(hit.id); })[0];
      if (selectedEvent) fillTeleport(context.selectedMapId, coordinateOrZero(selectedEvent.x), coordinateOrZero(selectedEvent.y));
      modal.remove();
      loadBrowse();
      renderEventList();
      renderEventPanel();
      toast("已选中事件 #" + hit.id);
    });
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
    if (action === "item-kind" || action === "render-items") return loadItems();
    if (action === "set-item") return run(function () { mod.setItemCount(context.itemKind, node.dataset.id, inputValue("tm-count-" + node.dataset.id)); }, function () { toast("数量已修改"); loadItems(); });
    if (action === "actor-select") {
      context.actorId = Number(node.dataset.id || node.value);
      context.skillQuery = "";
      context.stateQuery = "";
      context.equipSlot = null;
      context.equipKind = "";
      context.equipItemId = null;
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
    if (action === "equip-slot") {
      context.equipSlot = Number(node.value);
      var selectedEquipSlot = (mod.actorEquips(context.actorId) || []).filter(function (entry) { return Number(entry.slot) === context.equipSlot; })[0];
      context.equipKind = selectedEquipSlot && (selectedEquipSlot.type === "weapon" || selectedEquipSlot.type === "armor")
        ? selectedEquipSlot.type : (context.equipSlot === 0 ? "weapon" : "armor");
      context.equipItemId = selectedEquipSlot ? Number(selectedEquipSlot.id) || 0 : 0;
      return renderActorSection();
    }
    if (action === "equip-kind") {
      context.equipKind = node.value;
      var kindSlots = (mod.actorEquips(context.actorId) || []).filter(function (entry) { return entry.type === context.equipKind; });
      var nextSlot = kindSlots.filter(function (entry) { return Number(entry.slot) === Number(context.equipSlot); })[0] || kindSlots[0];
      context.equipSlot = nextSlot ? Number(nextSlot.slot) : null;
      context.equipItemId = nextSlot ? Number(nextSlot.id) || 0 : 0;
      return renderActorSection();
    }
    if (action === "pick-equip-slot") {
      context.equipSlot = Number(node.dataset.id);
      var pickedSlot = (mod.actorEquips(context.actorId) || []).filter(function (entry) { return Number(entry.slot) === context.equipSlot; })[0];
      context.equipKind = pickedSlot && (pickedSlot.type === "weapon" || pickedSlot.type === "armor") ? pickedSlot.type : (context.equipSlot === 0 ? "weapon" : "armor");
      context.equipItemId = pickedSlot ? Number(pickedSlot.id) || 0 : 0;
      return renderActorSection();
    }
    if (action === "pick-equip") {
      context.equipItemId = Number(node.dataset.id) || 0;
      var equipIdInput = root.querySelector("#tm-equip-id");
      if (equipIdInput) equipIdInput.value = String(context.equipItemId);
      return;
    }
    if (action === "recover-actor") return run(function () { mod.recoverActor(context.actorId); }, function () { toast("角色已恢复"); renderActorBody(); });
    if (action === "set-stat") return run(function () { mod.setActorStat(context.actorId, node.dataset.key, inputValue("tm-stat-" + node.dataset.key)); }, function () { toast("属性已修改"); renderActorBody(); });
    if (action === "learn-skill" || action === "forget-skill") return run(function () { var skillId = Number(node.dataset.id); return action === "learn-skill" ? mod.learnSkill(context.actorId, skillId) : mod.forgetSkill(context.actorId, skillId); }, function (entries) { refreshActorCollection("skills", entries); });
    if (action === "add-state" || action === "remove-state") return run(function () { var stateId = Number(node.dataset.id); return action === "add-state" ? mod.addState(context.actorId, stateId) : mod.removeState(context.actorId, stateId); }, function (entries) { refreshActorCollection("states", entries); });
    if (action === "change-equip") return run(function () {
      if (!inputValue("tm-equip-slot")) throw new Error("当前角色没有此类装备槽位");
      var slotIndex = Number(inputValue("tm-equip-slot")) || 0;
      var itemId = Number(inputValue("tm-equip-id")) || 0;
      var currentSlot = (mod.actorEquips(context.actorId) || []).filter(function (entry) { return Number(entry.slot) === slotIndex; })[0];
      var kind = itemId > 0 ? inputValue("tm-equip-kind") : (currentSlot && currentSlot.type) || inputValue("tm-equip-kind");
      mod.changeEquip(context.actorId, slotIndex, kind, itemId);
    }, function () { context.equipItemId = null; toast("装备已更换"); renderActorBody(); });
    if (action === "system-kind" || action === "render-system") return loadSystem();
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
        toast(enabled ? "所选地图事件独立开关已全开" : "所选地图事件独立开关已全关");
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
    if (action === "close-minimap") { var modal = root.querySelector(".tm-modal"); if (modal) modal.remove(); return; }
    if (action === "show-event-conditions") return showEventConditionsDialog(node.dataset.page);
    if (action === "close-conditions") { var conditions = root.querySelector(".tm-conditions-modal"); var overlay = conditions && conditions.parentNode; if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay); return; }
    if (action === "show-event-interpreter") return showEventInterpreterDialog(node.dataset.page);
    if (action === "close-interpreter") { var interpreter = root.querySelector(".tm-modal"); if (interpreter) interpreter.remove(); return; }
    if (action === "teleport") return run(function () { return mod.teleport(inputValue("tm-map-id"), inputValue("tm-map-x"), inputValue("tm-map-y"), 2, 0); }, function () { toast("已预约传送"); close(); });
    if (action === "set-enemy") { if (!isInBattleNow()) return; return run(function () { mod.setEnemyHp(node.dataset.index, inputValue("tm-enemy-" + node.dataset.index)); }, render); }
    if (action === "kill-enemies") { if (!isInBattleNow()) return; return run(mod.killAllEnemies, render); }
    if (action === "victory") { if (!isInBattleNow()) return; return run(mod.forceVictory, close); }
    if (action === "defeat") { if (!isInBattleNow()) return; return run(mod.forceDefeat, close); }
    if (action === "escape") { if (!isInBattleNow()) return; return run(mod.forceEscape, close); }
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
    root.innerHTML = '<div class="tm-panel" role="dialog" aria-modal="true"><div class="tm-head"><strong>RPG Maker RGSS 修改器</strong><button class="tm-refresh" data-action="render" title="刷新面板" aria-label="刷新面板">↻</button><button class="tm-close" data-action="close" aria-label="关闭">×</button></div><div class="tm-tabs">' + tabs.map(function (tab) { return '<button class="tm-tab" data-tab="' + tab[0] + '">' + tab[1] + '</button>'; }).join("") + '</div><main class="tm-content"></main></div>';
    document.documentElement.appendChild(root);
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
      var tab = event.target.closest("[data-tab]"); if (tab) { activeTab = tab.dataset.tab; render(); return; }
      var action = event.target.closest("[data-action]");
      if (action && action.type === "checkbox") return;
      if (action) handleAction(action.dataset.action, action);
    });
    root.addEventListener("change", function (event) {
      if (event.target.classList && event.target.classList.contains("tm-system-switch-input")) {
        var state = event.target.parentNode.querySelector(".tm-system-switch-state");
        if (state) state.textContent = event.target.checked ? "设为开" : "设为关";
      }
      if (event.target.id === "tm-gold") applyGoldInput(event.target);
      else if (event.target.id === "tm-equip-slot") handleAction("equip-slot", event.target);
      else if (event.target.id === "tm-equip-kind") handleAction("equip-kind", event.target);
      else { var action = event.target.closest("[data-action]"); if (action) handleAction(action.dataset.action, action); }
      event.stopPropagation();
    });
    root.addEventListener("keydown", function (event) {
      if (event.target && event.target.id === "tm-gold" && event.key === "Enter") {
        event.preventDefault();
        applyGoldInput(event.target);
        event.target.blur();
      }
    });
    root.addEventListener("input", function (event) {
      if (event.target.id === "tm-gold") fitGoldInput(event.target);
      else if (event.target.id === "tm-equip-id") context.equipItemId = event.target.value;
      else if (event.target.id === "tm-actor-query") { context.actorQuery = event.target.value; renderActorRoster(); }
      else if (event.target.id === "tm-skill-query") { context.skillQuery = event.target.value; renderActorCollectionList("skills"); }
      else if (event.target.id === "tm-state-query") { context.stateQuery = event.target.value; renderActorCollectionList("states"); }
    });
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
