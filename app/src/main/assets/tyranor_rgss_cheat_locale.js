(function () {
  "use strict";

  var languageTag = String(window.TyranorModLanguageTag || "").toLowerCase();
  var language = languageTag.indexOf("en") === 0 ? "en" : languageTag.indexOf("ja") === 0 ? "ja" : "zh";
  var dictionaries = {
    en: {
      "Tyranor RGSS 修改器": "Tyranor RGSS Cheats",
      "RPG Maker 修改器": "RPG Maker Cheats",
      "RPG Maker RGSS 修改器": "RPG Maker RGSS Cheats",
      "打开 RPG Maker 修改器": "Open RPG Maker Cheats",
      "关闭": "Close",
      "快捷": "Quick", "物品": "Items", "角色": "Characters", "系统": "System",
      "地图": "Map", "战斗": "Battle", "存档": "Saves", "键盘": "Keys", "说明": "About",
      "无敌": "Invincibility", "一击必杀": "One-hit kill", "必定暴击": "Always critical",
      "HP/MP不减": "Unlimited HP/MP", "TP不减": "Unlimited TP", "自定义点数不减": "Lock custom points",
      "金币不减": "Unlimited gold", "游戏币不减": "Unlimited coins", "异常状态免疫": "Status ailment immunity",
      "物品不减": "Unlimited items", "无遇敌": "No random encounters", "移动加速": "Speed boost",
      "快速文字": "Fast text", "必定逃跑成功": "Always escape", "技能不消耗": "Free skill cost",
      "角色与资源": "Character and resources", "探索": "Exploration", "战斗": "Battle",
      "移动速度": "Movement speed", "移动速度已设置": "Movement speed updated",
      "穿墙": "Walk through walls", "事件加速": "Speed up events", "对话快进": "Skip message pauses",
      "操作失败": "Operation failed", "完成": "Done", "请先进入游戏存档或开始新游戏，修改器正在等待 RPG Maker 运行时就绪。": "Load a save or start a new game. Waiting for the RPG Maker runtime.",
      "当前金币": "Current gold", "设置": "Set", "拉满": "Max", "全体恢复": "Recover party", "刷新页面": "Refresh",
      "运行时开关": "Runtime toggles", "金币": "Gold", "队伍": "Party", "物品数据库": "Item database",
      "加载中…": "Loading…", "搜索": "Search", "搜索名称": "Search names", "应用": "Apply",
      "没有匹配项目": "No matching entries", "当前队伍没有角色": "The party has no characters",
      "恢复": "Recover", "属性": "Stats", "技能": "Skills", "状态": "States", "装备": "Equipment",
      "技能 ID": "Skill ID", "学习": "Learn", "遗忘": "Forget", "状态 ID": "State ID", "附加": "Add", "移除": "Remove",
      "搜索技能名称或 ID": "Search skills by name or ID", "搜索状态名称或 ID": "Search states by name or ID",
      "槽位": "Slot", "武器": "Weapon", "护甲": "Armor", "装备 ID，0=卸下": "Equipment ID (0 to unequip)", "更换": "Change",
      "无": "None", "开关与变量": "Switches and variables", "开关": "Switch", "变量": "Variable", "名称或 ID": "Name or ID",
      "地图": "Map", "事件": "Events", "地图 ID": "Map ID", "传送": "Warp", "事件操作": "Event actions", "浏览": "Browse",
      "搜索地图名称 / ID": "Search map name or ID", "搜索 ID / 名称": "Search ID or name", "你在这里": "Current location",
      "选择": "Select", "没有匹配地图": "No matching maps", "当前地图没有事件（进入地图场景后打开）": "No events on this map. Open the panel from a map scene.",
      "在左侧选择事件查看详情": "Select an event on the left to view its details", "独立开关:": "Self switches:",
      ":开": ": On", ":关": ": Off", "全开": "All on", "全关": "All off", "迷你地图": "Minimap",
      "名称:": "Name:", "无法通行": "Impassable", "可通行": "Passable", "事件": "Event", "你": "Player",
      "点击事件块选中该事件；点外部区域或 × 关闭": "Tap an event tile to select it. Tap outside or × to close.",
      "已选中事件 #": "Selected event #", "当前不在战斗中": "Not in battle", "消灭全部敌人": "Defeat all enemies",
      "强制胜利": "Force victory", "强制逃跑": "Force escape", "战斗操作": "Battle actions", "敌方": "Enemies", "我方": "Party",
      "存档管理": "Save management", "读取中…": "Loading…", "存档 ": "Save ", "已有数据": "Existing data", "空": "Empty",
      "保存": "Save", "读取": "Load", "删除": "Delete", "Tyranor 修改器": "Tyranor Cheats", "版本 ": "Version ",
      "修改器只操作当前 RGSS 游戏的运行时对象。部分深度定制脚本可能覆盖标准引擎 API；遇到异常时可在单游戏设置中关闭修改器。": "Cheats only modify the current RGSS game's runtime objects. Custom scripts may override standard engine APIs. If errors occur, disable cheats in that game's settings.",
      "事件加速和对话快进可能跳过演出或等待，建议先保存游戏。": "Event speed and message skip may skip scenes or waits. Save first.",
      "虚拟按键": "Virtual controls", "按键层由应用原生实现，可自定义按键的位置、文字、大小与输出键位。进入编辑后：": "Virtual controls are native to the app. You can edit their positions, labels, sizes, and key outputs:",
      "点按按键选中，拖拽调整位置": "Tap a control to select it, then drag to move it", "右侧面板改文字 / 尺寸 / 键位": "Edit its label, size, or key on the side panel",
      "可复制、删除按键，或恢复默认": "Copy or delete controls, or restore defaults", "保存后立即生效": "Changes take effect after saving",
      "进入按键编辑": "Edit controls", "恢复默认": "Restore defaults", "当前未启用虚拟按键。可在应用的引擎设置中开启。": "Virtual controls are disabled. Enable them in the app's engine settings.",
      "设置已保存": "Setting saved", "金币已修改": "Gold updated", "金币已拉满": "Gold maxed", "队伍已恢复": "Party recovered",
      "数量已修改": "Quantity updated", "角色已恢复": "Character recovered", "属性已修改": "Stats updated", "装备已更换": "Equipment changed",
      "系统值已修改": "System value updated", "独立开关 A–D 已全开": "Self switches A–D enabled", "独立开关 A–D 已全关": "Self switches A–D disabled",
      "已预约传送": "Warp queued", "保存完成": "Save complete", "读取完成": "Load complete", "存档已删除": "Save deleted", "已恢复默认": "Defaults restored",
      "当前未启用虚拟按键": "Virtual controls are disabled", "RGSS 修改器桥未连接": "RGSS cheat bridge is unavailable",
      "当前类别": "Category", "物品数据库": "Item database", "地图列表不可用": "Map list unavailable", "操作失败": "Operation failed",
      "已有数据": "Existing data", "空": "Empty", "全开": "All on", "全关": "All off"
    },
    ja: {
      "Tyranor RGSS 修改器": "Tyranor RGSS 改造パネル",
      "RPG Maker 修改器": "RPG Maker 改造パネル",
      "RPG Maker RGSS 修改器": "RPG Maker RGSS 改造パネル",
      "打开 RPG Maker 修改器": "RPG Maker 改造パネルを開く",
      "关闭": "閉じる",
      "快捷": "クイック", "物品": "アイテム", "角色": "キャラクター", "系统": "システム",
      "地图": "マップ", "战斗": "戦闘", "存档": "セーブ", "键盘": "キー設定", "说明": "説明",
      "无敌": "無敵", "一击必杀": "一撃必殺", "必定暴击": "クリティカル確定",
      "HP/MP不减": "HP/MP 減少なし", "TP不减": "TP 減少なし", "自定义点数不减": "カスタムポイント固定",
      "金币不减": "所持金減少なし", "游戏币不减": "コイン減少なし", "异常状态免疫": "状態異常無効",
      "物品不减": "アイテム減少なし", "无遇敌": "エンカウントなし", "移动加速": "移動速度アップ",
      "快速文字": "メッセージ高速化", "必定逃跑成功": "逃走成功確定", "技能不消耗": "スキル消費なし",
      "角色与资源": "キャラクターと所持品", "探索": "探索", "战斗": "戦闘",
      "移动速度": "移動速度", "移动速度已设置": "移動速度を変更しました",
      "穿墙": "壁抜け", "事件加速": "イベント高速化", "对话快进": "メッセージ早送り",
      "操作失败": "操作に失敗しました", "完成": "完了", "请先进入游戏存档或开始新游戏，修改器正在等待 RPG Maker 运行时就绪。": "セーブデータを読み込むか、新しいゲームを開始してください。RPG Maker の起動を待っています。",
      "当前金币": "現在の所持金", "设置": "設定", "拉满": "最大", "全体恢复": "パーティ全回復", "刷新页面": "更新",
      "运行时开关": "実行中のチート", "金币": "所持金", "队伍": "パーティ", "物品数据库": "アイテム一覧",
      "加载中…": "読み込み中…", "搜索": "検索", "搜索名称": "名前を検索", "应用": "適用",
      "没有匹配项目": "該当する項目はありません", "当前队伍没有角色": "パーティにキャラクターがいません",
      "恢复": "回復", "属性": "能力値", "技能": "スキル", "状态": "状態", "装备": "装備",
      "技能 ID": "スキル ID", "学习": "習得", "遗忘": "忘れる", "状态 ID": "ステート ID", "附加": "追加", "移除": "解除",
      "搜索技能名称或 ID": "スキル名または ID を検索", "搜索状态名称或 ID": "ステート名または ID を検索",
      "槽位": "スロット", "武器": "武器", "护甲": "防具", "装备 ID，0=卸下": "装備 ID（0 で解除）", "更换": "変更",
      "无": "なし", "开关与变量": "スイッチと変数", "开关": "スイッチ", "变量": "変数", "名称或 ID": "名前または ID",
      "事件": "イベント", "地图 ID": "マップ ID", "传送": "移動", "事件操作": "イベント操作", "浏览": "一覧",
      "搜索地图名称 / ID": "マップ名 / ID を検索", "搜索 ID / 名称": "ID / 名前を検索", "你在这里": "現在地",
      "选择": "選択", "没有匹配地图": "該当するマップはありません", "当前地图没有事件（进入地图场景后打开）": "このマップにイベントはありません。マップ画面で開いてください。",
      "在左侧选择事件查看详情": "左側のイベントを選択すると詳細を表示します", "独立开关:": "セルフスイッチ:",
      ":开": ": オン", ":关": ": オフ", "全开": "すべてオン", "全关": "すべてオフ", "迷你地图": "ミニマップ",
      "名称:": "名前:", "无法通行": "通行不可", "可通行": "通行可", "你": "プレイヤー",
      "点击事件块选中该事件；点外部区域或 × 关闭": "イベントをタップして選択します。外側または × をタップすると閉じます。",
      "已选中事件 #": "選択中のイベント #", "当前不在战斗中": "戦闘中ではありません", "消灭全部敌人": "敵をすべて倒す",
      "强制胜利": "強制勝利", "强制逃跑": "強制逃走", "战斗操作": "戦闘操作", "敌方": "敵", "我方": "味方",
      "存档管理": "セーブ管理", "读取中…": "読み込み中…", "存档 ": "セーブ ", "已有数据": "データあり", "空": "空",
      "保存": "保存", "读取": "読み込み", "删除": "削除", "Tyranor 修改器": "Tyranor 改造パネル", "版本 ": "バージョン ",
      "修改器只操作当前 RGSS 游戏的运行时对象。部分深度定制脚本可能覆盖标准引擎 API；遇到异常时可在单游戏设置中关闭修改器。": "このパネルは現在の RGSS ゲームの実行中データを変更します。独自スクリプトが標準 API を上書きする場合があります。エラーが発生した場合は、そのゲームの設定で無効にしてください。",
      "事件加速和对话快进可能跳过演出或等待，建议先保存游戏。": "イベント高速化とメッセージ早送りは演出や待機を飛ばす場合があります。先にセーブしてください。",
      "虚拟按键": "仮想ボタン", "按键层由应用原生实现，可自定义按键的位置、文字、大小与输出键位。进入编辑后：": "仮想ボタンはアプリのネイティブ機能です。位置、ラベル、サイズ、出力キーを編集できます：",
      "点按按键选中，拖拽调整位置": "ボタンをタップして選択し、ドラッグして移動", "右侧面板改文字 / 尺寸 / 键位": "右側のパネルでラベル / サイズ / キーを変更",
      "可复制、删除按键，或恢复默认": "ボタンの複製・削除、初期化が可能", "保存后立即生效": "保存後すぐに反映",
      "进入按键编辑": "ボタンを編集", "恢复默认": "初期設定に戻す", "当前未启用虚拟按键。可在应用的引擎设置中开启。": "仮想ボタンは無効です。アプリのエンジン設定で有効にできます。",
      "设置已保存": "設定を保存しました", "金币已修改": "所持金を変更しました", "金币已拉满": "所持金を最大にしました", "队伍已恢复": "パーティを回復しました",
      "数量已修改": "数量を変更しました", "角色已恢复": "キャラクターを回復しました", "属性已修改": "能力値を変更しました", "装备已更换": "装備を変更しました",
      "系统值已修改": "システム値を変更しました", "独立开关 A–D 已全开": "セルフスイッチ A–D をオンにしました", "独立开关 A–D 已全关": "セルフスイッチ A–D をオフにしました",
      "已预约传送": "移動を予約しました", "保存完成": "保存しました", "读取完成": "読み込みました", "存档已删除": "セーブを削除しました", "已恢复默认": "初期設定に戻しました",
      "当前未启用虚拟按键": "仮想ボタンは無効です", "RGSS 修改器桥未连接": "RGSS 改造ブリッジに接続できません",
      "当前类别": "種類", "地图列表不可用": "マップ一覧を利用できません", "已有数据": "データあり", "空": "空",
      "全开": "すべてオン", "全关": "すべてオフ"
    }
  };

  var dictionary = dictionaries[language] || {};
  function translate(value) {
    var text = String(value == null ? "" : value);
    if (dictionary[text]) return dictionary[text];
    var dynamic = text.match(/^(槽位\s*\d+：|存档\s+|迷你地图 #|已选中事件 #)/);
    if (dynamic) {
      var prefix = dynamic[0];
      var translatedPrefix = prefix.indexOf("槽位") === 0
        ? (language === "ja" ? "スロット" : "Slot ") + prefix.match(/\d+/)[0] + (language === "ja" ? "：" : ": ")
        : prefix.indexOf("存档") === 0 ? (language === "ja" ? "セーブ " : "Save ")
        : prefix.indexOf("迷你地图") === 0 ? (language === "ja" ? "ミニマップ #" : "Minimap #")
        : (language === "ja" ? "選択中のイベント #" : "Selected event #");
      var suffix = text.substring(prefix.length);
      if (prefix.indexOf("存档") === 0) {
        suffix = suffix.replace(/ · 已有数据$/, " · " + translate("已有数据"));
        suffix = suffix.replace(/ · 空$/, " · " + translate("空"));
      }
      return translatedPrefix + suffix;
    }
    var switchState = text.match(/^([A-D]):(开|关)$/);
    if (switchState) return switchState[1] + ":" + translate(switchState[2]);
    var noclipState = text.match(/^穿墙:(开|关)$/);
    if (noclipState) return translate("穿墙") + ":" + translate(noclipState[1]);
    return text;
  }
  function localizeNode(node) {
    if (!node) return;
    if (node.nodeType === 3) {
      var parent = node.parentElement;
      if (parent && (parent.closest(".tm-name") || parent.closest("#tm-actor"))) return;
      var raw = node.nodeValue;
      var trimmed = raw.trim();
      if (!trimmed) return;
      var localized = translate(trimmed);
      if (localized === trimmed && /^版本\s+/.test(trimmed)) localized = translate("版本 ") + trimmed.substring(trimmed.indexOf(" ") + 1);
      if (localized !== trimmed) node.nodeValue = raw.replace(trimmed, localized);
      return;
    }
    if (node.nodeType !== 1) return;
    ["placeholder", "aria-label", "title"].forEach(function (name) {
      if (!node.hasAttribute(name)) return;
      var value = node.getAttribute(name);
      var localized = translate(value);
      if (localized !== value) node.setAttribute(name, localized);
    });
    Array.prototype.slice.call(node.childNodes).forEach(localizeNode);
  }

  if (languageTag) document.documentElement.lang = languageTag;
  if (language !== "zh" && typeof MutationObserver !== "undefined") {
    localizeNode(document.documentElement);
    new MutationObserver(function (records) {
      records.forEach(function (record) {
        if (record.type === "characterData") localizeNode(record.target);
        else if (record.type === "childList") Array.prototype.forEach.call(record.addedNodes, localizeNode);
        else if (record.type === "attributes") localizeNode(record.target);
      });
    }).observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["placeholder", "aria-label", "title"],
      characterData: true,
      childList: true,
      subtree: true
    });
  }
  window.TyranorCheatTranslate = translate;
})();
