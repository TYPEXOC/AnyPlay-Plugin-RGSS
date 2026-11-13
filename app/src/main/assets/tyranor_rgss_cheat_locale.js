(function () {
  "use strict";

  var languageTag = String(window.TyranorModLanguageTag || "").toLowerCase();
  var language = languageTag.indexOf("en") === 0 ? "en" : languageTag.indexOf("ja") === 0 ? "ja" : "zh";
  var dictionaries = {
    zh: { "装备动作": "装备", "卸下动作": "卸下", "事件页开关": "开关", "事件解释器按钮": "事件解释器", "宝可梦修改器": "宝可梦修改器", "HP/PP不减": "HP/PP不减" },
    en: {
      "Tyranor RGSS 修改器": "Tyranor RGSS Cheats",
      "RPG Maker 修改器": "RPG Maker Cheats",
      "RPG Maker RGSS 修改器": "RPG Maker RGSS Cheats",
      "宝可梦修改器": "Pokémon Cheats",
      "打开 RPG Maker 修改器": "Open RPG Maker Cheats",
      "关闭": "Close",
      "快捷": "Quick", "物品": "Items", "角色": "Characters", "系统": "System",
      "地图": "Map", "战斗": "Battle", "存档": "Saves", "键盘": "Keys", "说明": "About",
      "无敌": "Invincibility", "一击必杀": "One-hit kill", "必定暴击": "Always critical",
      "HP/MP不减": "Unlimited HP/MP", "TP不减": "Unlimited TP", "自定义点数不减": "Lock custom points",
      "HP/PP不减": "Unlimited HP/PP",
      "金币不减": "Unlimited gold", "游戏币不减": "Unlimited coins", "异常状态免疫": "Status ailment immunity",
      "物品不减": "Unlimited items", "无遇敌": "No random encounters", "移动加速": "Speed boost",
      "快速文字": "Fast text", "必定逃跑成功": "Always escape", "技能不消耗": "Free skill cost",
      "角色与资源": "Character and resources", "探索": "Exploration", "战斗": "Battle",
      "移动速度": "Movement speed", "移动速度已设置": "Movement speed updated", "游戏倍速": "Game speed", "游戏倍速已设置": "Game speed updated",
      "经验倍率": "EXP multiplier", "掉落倍率": "Drop multiplier", "战斗奖励倍率": "Battle reward multipliers", "经验倍率已设置": "EXP multiplier updated", "掉落倍率已设置": "Drop multiplier updated",
      "穿墙": "Walk through walls", "事件加速": "Speed up events", "对话快进": "Skip message pauses",
      "操作失败": "Operation failed", "完成": "Done", "请先进入游戏存档或开始新游戏，修改器正在等待 RPG Maker 运行时就绪。": "Load a save or start a new game. Waiting for the RPG Maker runtime.",
      "当前金币": "Current gold", "金币：": "Gold:", "设置": "Set", "拉满": "Max", "全体恢复": "Recover party", "刷新面板": "Refresh panel",
      "当前没有可用的快捷功能": "No quick actions are available",
      "运行时开关": "Runtime toggles", "金币": "Gold", "队伍": "Party", "物品数据库": "Item database", "物品详情": "Item details", "请选择物品查看详情": "Select an item to view its details", "数量操作": "Quantity controls", "当前数量": "Owned", "已选择": "Selected",
      "加载中…": "Loading…", "搜索": "Search", "搜索名称": "Search names", "应用": "Apply", "数量": "Quantity",
      "锁定": "Lock", "名称": "Name", "当前值": "Current value",
      "物品已锁定": "Item quantity locked", "系统值已锁定": "System value locked", "已取消锁定": "Lock removed",
      "没有匹配项目": "No matching entries", "当前队伍没有角色": "The party has no characters", "当前游戏没有角色": "This game has no characters",
      "队伍管理": "Party management", "在队伍中": "In party", "不在队伍中": "Not in party", "搜索角色名称或 ID": "Search characters",
      "恢复": "Recover", "属性": "Stats", "技能": "Skills", "状态": "States", "装备": "Equipment",
      "宝可梦": "Pokémon", "当前游戏不支持宝可梦队伍修改": "This game does not support Pokémon party editing", "当前队伍里没有宝可梦": "There are no Pokémon in the current party", "蛋": "Egg", "蛋不能在这里编辑或恢复": "Eggs cannot be edited or recovered here",
      "当前 HP": "Current HP", "招式": "Moves", "携带物品": "Held item", "IV / EV": "IV / EV", "IV": "IV", "EV": "EV", "状态：": "Status: ", "持有物：": "Held item: ", "招式位": "Move slot", "空": "Empty", "编辑招式": "Edit moves", "替换": "Replace", "清空": "Clear", "选择替换招式": "Choose a replacement move", "搜索名称或编号": "Search by name or ID", "清除状态": "Clear status", "编辑状态": "Edit status", "设置携带物品": "Set held item", "不持有物品": "No held item", "结果过多，请输入更具体的搜索词": "Too many results; enter a more specific search", "编辑 IV / EV": "Edit IV / EV",
      "数值已修改": "Value updated", "宝可梦已恢复": "Pokémon recovered", "招式已更新": "Moves updated", "状态已更新": "Status updated", "携带物品已更新": "Held item updated", "没有可用的招式位": "No move slots are available", "请输入有效的非负整数": "Enter a valid non-negative integer",
      "睡眠": "Sleep", "中毒": "Poison", "灼伤": "Burn", "麻痹": "Paralysis", "冰冻": "Frozen", "其他状态": "Other status", "特攻": "Special Attack", "特防": "Special Defense", "速度": "Speed",
      "职业": "Class", "选择职业": "Select a class", "更换职业": "Change class", "职业已更换": "Class changed", "当前游戏没有职业列表": "No class data is available in this game", "请先选择职业": "Select a class first",
      "等级": "Level", "经验": "EXP", "最大 HP": "Max HP", "最大 MP": "Max MP", "攻击": "Attack", "防御": "Defense", "魔攻": "Magic Attack", "魔防": "Magic Defense", "敏捷": "Agility", "幸运": "Luck",
      "已学会技能": "Learned skills", "可学习技能": "Available skills", "当前状态": "Active states", "可附加状态": "Available states",
      "隐藏空选项": "Hide unnamed entries",
      "没有已学技能": "No learned skills", "没有可学习技能": "No skills available to learn", "没有当前状态": "No active states", "没有可附加状态": "No states available to add",
      "技能 ID": "Skill ID", "学习": "Learn", "遗忘": "Forget", "状态 ID": "State ID", "附加": "Add", "移除": "Remove",
      "搜索技能名称或 ID": "Search skills by name or ID", "搜索状态名称或 ID": "Search states by name or ID",
      "槽位": "Slot", "武器": "Weapon", "护甲": "Armor", "装备 ID": "Equipment ID", "装备 ID，0=卸下": "Equipment ID (0 to unequip)", "装备槽位": "Equipment slot", "装备类型": "Equipment type", "选择装备": "Select equipment", "当前装备": "Current equipment", "当前装备：": "Equipped:", "当前槽位装备": "Equipped slots", "可选武器": "Available weapons", "可选护甲": "Available armor", "选择左侧槽位后，点击装备": "Choose a slot on the left, then tap Equip", "当前槽位没有可选装备": "No compatible equipment is available for this slot", "当前角色没有此类槽位": "This character has no slot for this type", "卸下装备": "Unequip", "装备动作": "Equip", "卸下动作": "Unequip", "没有可选装备": "No available equipment", "当前角色没有可修改的装备槽位": "This character has no editable equipment slots", "更换": "Change",
      "无": "None", "开关与变量": "Switches and variables", "开关": "Switch", "变量": "Variable", "设为开": "Set on", "设为关": "Set off", "名称或 ID": "Name or ID",
      "地图": "Map", "事件": "Events", "地图 ID": "Map ID", "传送": "Warp", "地图操作": "Map actions", "传送到事件": "Warp to event", "事件详情": "Event details", "独立开关": "Self switch", "事件开关全开": "Enable all event switches", "事件开关全关": "Disable all event switches", "事件操作": "Event actions", "浏览": "Browse",
      "搜索地图名称 / ID": "Search map name or ID", "搜索 ID / 名称": "Search ID or name", "你在这里": "Current location",
      "选择": "Select", "没有匹配地图": "No matching maps", "当前地图没有事件（进入地图场景后打开）": "No events on this map. Open the panel from a map scene.",
      "在左侧选择事件查看详情": "Select an event on the left to view its details", "独立开关:": "Self switches:",
      ":开": ": On", ":关": ": Off", "全开": "All on", "全关": "All off", "迷你地图": "Minimap",
      "名称:": "Name:", "无法通行": "Impassable", "可通行": "Passable", "事件": "Event", "你": "Player",
      "点击事件块选中该事件；点外部区域或 × 关闭": "Tap an event tile to select it. Tap outside or × to close.",
      "已选中事件 #": "Selected event #", "当前不在战斗中": "Not in battle", "消灭全部敌人": "Defeat all enemies",
      "强制胜利": "Force victory", "强制战败": "Force defeat", "强制逃跑": "Force escape", "战斗操作": "Battle actions", "战斗开关": "Battle modifiers", "当前游戏没有可用的战斗开关": "No battle modifiers are available in this game", "敌方": "Enemies", "我方": "Party",
      "所选地图全部事件独立开关:": "Self-switches for all events on selected map:", "全部开启": "Enable all", "全部关闭": "Disable all", "该事件独立开关:": "Selected event self-switches:", "所选地图事件开关已全开": "Enabled switches for all events on selected map", "所选地图事件开关已全关": "Disabled switches for all events on selected map",
      "事件条件开关已更新": "Event condition switch updated",
      "名称:": "Name:", "描述:": "Description:", "独立开关:": "Self-switches:", "事件解释器": "Event interpreter", "事件解释器按钮": "Interpreter", "事件页开关": "Cond.", "事件条件": "Event conditions", "条件": "Conditions", "点击开关或独立开关条件可单独切换": "Tap a switch or self-switch condition to toggle it", "事件长度": "Commands", "无条件": "No conditions", "无法读取所选事件详情": "Unable to read the selected event", "事件没有可显示的页面": "This event has no pages to display", "此事件页没有指令": "This event page has no commands", "点击指令可展开查看参数": "Tap a command to expand its parameters", "开": "On", "关": "Off", "指令数": "Commands", "条件数": "Conditions",
      "结束": "End", "显示文本": "Show Text", "显示选项": "Show Choices", "数值输入": "Input Number", "物品选择": "Select Item", "滚动文本": "Scroll Text", "注释": "Comment", "条件分支": "Conditional Branch", "循环": "Loop", "中断循环": "Break Loop", "事件处理结束": "Exit Event Processing", "调用公共事件": "Call Common Event", "标签": "Label", "跳转标签": "Jump to Label", "控制开关": "Control Switches", "控制变量": "Control Variables", "控制独立开关": "Control Self Switch", "传送玩家": "Transfer Player", "设置事件位置": "Set Event Location", "设置移动路线": "Set Move Route", "等待": "Wait", "播放 BGM": "Play BGM", "播放 BGS": "Play BGS", "播放 ME": "Play ME", "播放 SE": "Play SE", "战斗处理": "Battle Processing", "商店处理": "Shop Processing", "增减 HP": "Change HP", "增减 MP": "Change MP", "更改状态": "Change State", "全体恢复": "Recover All", "打开菜单": "Open Menu", "打开存档画面": "Open Save Screen", "游戏结束": "Game Over", "脚本": "Script", "文本内容": "Text", "选项分支": "Choice Branch", "取消分支": "Cancel Branch", "结束选项分支": "End Choice Branch", "结束条件分支": "End Branch", "重复循环": "Repeat Above", "胜利时": "When Victory", "逃跑时": "When Escape", "失败时": "When Defeat", "结束战斗分支": "End Battle Branch", "脚本行": "Script Line", "事件指令": "Event Command",
      "存档管理": "Save management", "读取中…": "Loading…", "存档 ": "Save ", "已有数据": "Existing data", "空": "Empty",
      "保存": "Save", "覆盖": "Overwrite", "读取": "Load", "删除": "Delete", "Tyranor 修改器": "Tyranor Cheats", "版本 ": "Version ",
      "修改器只操作当前 RGSS 游戏的运行时对象。部分深度定制脚本可能覆盖标准引擎 API；遇到异常时可在单游戏设置中关闭修改器。": "Cheats only modify the current RGSS game's runtime objects. Custom scripts may override standard engine APIs. If errors occur, disable cheats in that game's settings.",
      "事件加速和对话快进可能跳过演出或等待，建议先保存游戏。": "Event speed and message skip may skip scenes or waits. Save first.",
      "虚拟按键": "Virtual controls", "按键层由应用原生实现，可自定义按键的位置、文字、大小与输出键位。进入编辑后：": "Virtual controls are native to the app. You can edit their positions, labels, sizes, and key outputs:",
      "点按按键选中，拖拽调整位置": "Tap a control to select it, then drag to move it", "右侧面板改文字 / 尺寸 / 键位": "Edit its label, size, or key on the side panel",
      "可复制、删除按键，或恢复默认": "Copy or delete controls, or restore defaults", "保存后立即生效": "Changes take effect after saving",
      "进入按键编辑": "Edit controls", "恢复默认": "Restore defaults", "当前未启用虚拟按键。可在应用的引擎设置中开启。": "Virtual controls are disabled. Enable them in the app's engine settings.",
      "设置已保存": "Setting saved", "金币已修改": "Gold updated", "金币已拉满": "Gold maxed", "队伍已恢复": "Party recovered",
      "数量已修改": "Quantity updated", "角色已恢复": "Character recovered", "队伍成员已更新": "Party membership updated", "属性已修改": "Stats updated", "装备已更换": "Equipment changed", "装备已卸下": "Equipment unequipped",
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
      "宝可梦修改器": "ポケモン改造パネル",
      "打开 RPG Maker 修改器": "RPG Maker 改造パネルを開く",
      "关闭": "閉じる",
      "快捷": "クイック", "物品": "アイテム", "角色": "キャラクター", "系统": "システム",
      "地图": "マップ", "战斗": "戦闘", "存档": "セーブ", "键盘": "キー設定", "说明": "説明",
      "无敌": "無敵", "一击必杀": "一撃必殺", "必定暴击": "クリティカル確定",
      "HP/MP不减": "HP/MP 減少なし", "TP不减": "TP 減少なし", "自定义点数不减": "カスタムポイント固定",
      "HP/PP不减": "HP/PP 減少なし",
      "金币不减": "所持金減少なし", "游戏币不减": "コイン減少なし", "异常状态免疫": "状態異常無効",
      "物品不减": "アイテム減少なし", "无遇敌": "エンカウントなし", "移动加速": "移動速度アップ",
      "快速文字": "メッセージ高速化", "必定逃跑成功": "逃走成功確定", "技能不消耗": "スキル消費なし",
      "角色与资源": "キャラクターと所持品", "探索": "探索", "战斗": "戦闘",
      "移动速度": "移動速度", "移动速度已设置": "移動速度を変更しました", "游戏倍速": "ゲーム速度", "游戏倍速已设置": "ゲーム速度を変更しました",
      "经验倍率": "経験値倍率", "掉落倍率": "ドロップ倍率", "战斗奖励倍率": "戦闘報酬倍率", "经验倍率已设置": "経験値倍率を変更しました", "掉落倍率已设置": "ドロップ倍率を変更しました",
      "穿墙": "壁抜け", "事件加速": "イベント高速化", "对话快进": "メッセージ早送り",
      "操作失败": "操作に失敗しました", "完成": "完了", "请先进入游戏存档或开始新游戏，修改器正在等待 RPG Maker 运行时就绪。": "セーブデータを読み込むか、新しいゲームを開始してください。RPG Maker の起動を待っています。",
      "当前金币": "現在の所持金", "金币：": "所持金:", "设置": "設定", "拉满": "最大", "全体恢复": "パーティ全回復", "刷新面板": "パネルを更新",
      "当前没有可用的快捷功能": "利用可能なクイック操作はありません",
      "运行时开关": "実行中のチート", "金币": "所持金", "队伍": "パーティ", "物品数据库": "アイテム一覧", "物品详情": "アイテム詳細", "请选择物品查看详情": "アイテムを選択すると詳細が表示されます", "数量操作": "個数の操作", "当前数量": "現在の個数", "已选择": "選択済み",
      "加载中…": "読み込み中…", "搜索": "検索", "搜索名称": "名前を検索", "应用": "適用", "数量": "個数",
      "锁定": "固定", "名称": "名前", "当前值": "現在値",
      "物品已锁定": "アイテム数を固定しました", "系统值已锁定": "システム値を固定しました", "已取消锁定": "固定を解除しました",
      "没有匹配项目": "該当する項目はありません", "当前队伍没有角色": "パーティにキャラクターがいません", "当前游戏没有角色": "このゲームにキャラクターがいません",
      "队伍管理": "パーティ管理", "在队伍中": "パーティにいる", "不在队伍中": "パーティにいない", "搜索角色名称或 ID": "キャラクター名または ID を検索",
      "恢复": "回復", "属性": "能力値", "技能": "スキル", "状态": "状態", "装备": "装備",
      "宝可梦": "ポケモン", "当前游戏不支持宝可梦队伍修改": "このゲームではパーティーのポケモン編集に対応していません", "当前队伍里没有宝可梦": "現在のパーティーにポケモンがいません", "蛋": "タマゴ", "蛋不能在这里编辑或恢复": "タマゴはここで編集・回復できません",
      "当前 HP": "現在 HP", "招式": "わざ", "携带物品": "持ち物", "IV / EV": "個体値 / 努力値", "IV": "個体値", "EV": "努力値", "状态：": "状態：", "持有物：": "持ち物：", "招式位": "わざ枠", "空": "空き", "编辑招式": "わざを編集", "替换": "変更", "清空": "クリア", "选择替换招式": "入れ替えるわざを選択", "搜索名称或编号": "名前または ID で検索", "清除状态": "状態異常を治す", "编辑状态": "状態を編集", "设置携带物品": "持ち物を設定", "不持有物品": "持ち物なし", "结果过多，请输入更具体的搜索词": "結果が多すぎます。検索語を絞ってください", "编辑 IV / EV": "個体値 / 努力値を編集",
      "数值已修改": "数値を更新しました", "宝可梦已恢复": "ポケモンを回復しました", "招式已更新": "わざを更新しました", "状态已更新": "状態を更新しました", "携带物品已更新": "持ち物を更新しました", "没有可用的招式位": "使用できるわざ枠がありません", "请输入有效的非负整数": "0 以上の整数を入力してください",
      "睡眠": "ねむり", "中毒": "どく", "灼伤": "やけど", "麻痹": "まひ", "冰冻": "こおり", "其他状态": "その他の状態", "特攻": "特攻", "特防": "特防", "速度": "すばやさ",
      "职业": "職業", "选择职业": "職業を選択", "更换职业": "職業を変更", "职业已更换": "職業を変更しました", "当前游戏没有职业列表": "職業データがありません", "请先选择职业": "職業を選択してください",
      "等级": "レベル", "经验": "経験値", "最大 HP": "最大 HP", "最大 MP": "最大 MP", "攻击": "攻撃", "防御": "防御", "魔攻": "魔法攻撃", "魔防": "魔法防御", "敏捷": "敏捷", "幸运": "運",
      "已学会技能": "習得済みスキル", "可学习技能": "習得可能なスキル", "当前状态": "有効なステート", "可附加状态": "追加可能なステート",
      "隐藏空选项": "名前のない項目を隠す",
      "没有已学技能": "習得済みスキルはありません", "没有可学习技能": "習得できるスキルはありません", "没有当前状态": "有効なステートはありません", "没有可附加状态": "追加できるステートはありません",
      "技能 ID": "スキル ID", "学习": "習得", "遗忘": "忘れる", "状态 ID": "ステート ID", "附加": "追加", "移除": "解除",
      "搜索技能名称或 ID": "スキル名または ID を検索", "搜索状态名称或 ID": "ステート名または ID を検索",
      "槽位": "スロット", "武器": "武器", "护甲": "防具", "装备 ID": "装備 ID", "装备 ID，0=卸下": "装備 ID（0 で解除）", "装备槽位": "装備スロット", "装备类型": "装備タイプ", "选择装备": "装備を選択", "当前装备": "現在の装備", "当前装备：": "装備中:", "当前槽位装备": "装備中のスロット", "可选武器": "選択できる武器", "可选护甲": "選択できる防具", "选择左侧槽位后，点击装备": "左側のスロットを選び、装備をタップ", "当前槽位没有可选装备": "このスロットに装備できるアイテムはありません", "当前角色没有此类槽位": "この種類の装備スロットはありません", "卸下装备": "装備を外す", "装备动作": "装備", "卸下动作": "解除", "没有可选装备": "選択できる装備がありません", "当前角色没有可修改的装备槽位": "変更できる装備スロットがありません", "更换": "変更",
      "无": "なし", "开关与变量": "スイッチと変数", "开关": "スイッチ", "变量": "変数", "设为开": "オンにする", "设为关": "オフにする", "名称或 ID": "名前または ID",
      "事件": "イベント", "地图 ID": "マップ ID", "传送": "移動", "地图操作": "マップ操作", "传送到事件": "イベントへ移動", "事件详情": "イベント詳細", "独立开关": "セルフスイッチ", "事件开关全开": "イベントスイッチをすべてオン", "事件开关全关": "イベントスイッチをすべてオフ", "事件操作": "イベント操作", "浏览": "一覧",
      "搜索地图名称 / ID": "マップ名 / ID を検索", "搜索 ID / 名称": "ID / 名前を検索", "你在这里": "現在地",
      "选择": "選択", "没有匹配地图": "該当するマップはありません", "当前地图没有事件（进入地图场景后打开）": "このマップにイベントはありません。マップ画面で開いてください。",
      "在左侧选择事件查看详情": "左側のイベントを選択すると詳細を表示します", "独立开关:": "セルフスイッチ:",
      ":开": ": オン", ":关": ": オフ", "全开": "すべてオン", "全关": "すべてオフ", "迷你地图": "ミニマップ",
      "名称:": "名前:", "无法通行": "通行不可", "可通行": "通行可", "你": "プレイヤー",
      "点击事件块选中该事件；点外部区域或 × 关闭": "イベントをタップして選択します。外側または × をタップすると閉じます。",
      "已选中事件 #": "選択中のイベント #", "当前不在战斗中": "戦闘中ではありません", "消灭全部敌人": "敵をすべて倒す",
      "强制胜利": "強制勝利", "强制战败": "強制敗北", "强制逃跑": "強制逃走", "战斗操作": "戦闘操作", "战斗开关": "戦闘チート", "当前游戏没有可用的战斗开关": "このゲームで使える戦闘チートはありません", "敌方": "敵", "我方": "味方",
      "所选地图全部事件独立开关:": "選択したマップの全イベントセルフスイッチ:", "全部开启": "すべてオン", "全部关闭": "すべてオフ", "该事件独立开关:": "選択中イベントのセルフスイッチ:", "所选地图事件开关已全开": "選択したマップの全イベントスイッチをオンにしました", "所选地图事件开关已全关": "選択したマップの全イベントスイッチをオフにしました",
      "事件条件开关已更新": "イベント条件スイッチを更新しました",
      "名称:": "名前:", "描述:": "説明:", "独立开关:": "セルフスイッチ:", "事件解释器": "イベントインタプリタ", "事件解释器按钮": "インタプリタ", "事件页开关": "条件", "事件条件": "イベント条件", "条件": "条件", "点击开关或独立开关条件可单独切换": "スイッチまたはセルフスイッチ条件をタップして切り替え", "事件长度": "コマンド数", "无条件": "条件なし", "无法读取所选事件详情": "選択したイベントを読み込めません", "事件没有可显示的页面": "表示できるページがありません", "此事件页没有指令": "このイベントページにコマンドはありません", "点击指令可展开查看参数": "タップするとコマンドのパラメーターを表示します", "开": "オン", "关": "オフ", "指令数": "コマンド数", "条件数": "条件数",
      "结束": "終了", "显示文本": "文章の表示", "显示选项": "選択肢の表示", "数值输入": "数値入力", "物品选择": "アイテム選択", "滚动文本": "スクロール文章", "注释": "注釈", "条件分支": "条件分岐", "循环": "ループ", "中断循环": "ループ中断", "事件处理结束": "イベント処理の終了", "调用公共事件": "コモンイベント", "标签": "ラベル", "跳转标签": "ラベルジャンプ", "控制开关": "スイッチの操作", "控制变量": "変数の操作", "控制独立开关": "セルフスイッチの操作", "传送玩家": "場所移動", "设置事件位置": "イベント位置設定", "设置移动路线": "移動ルートの設定", "等待": "ウェイト", "播放 BGM": "BGM の演奏", "播放 BGS": "BGS の演奏", "播放 ME": "ME の演奏", "播放 SE": "SE の演奏", "战斗处理": "戦闘の処理", "商店处理": "ショップの処理", "增减 HP": "HP の増減", "增减 MP": "MP の増減", "更改状态": "ステートの変更", "全体恢复": "全回復", "打开菜单": "メニュー画面", "打开存档画面": "セーブ画面", "游戏结束": "ゲーム終了", "脚本": "スクリプト", "文本内容": "文章", "选项分支": "選択肢分岐", "取消分支": "キャンセル分岐", "结束选项分支": "選択肢分岐終了", "结束条件分支": "条件分岐終了", "重复循环": "ループの再開", "胜利时": "勝利時", "逃跑时": "逃走時", "失败时": "敗北時", "结束战斗分支": "戦闘分岐終了", "脚本行": "スクリプト行", "事件指令": "イベントコマンド",
      "存档管理": "セーブ管理", "读取中…": "読み込み中…", "存档 ": "セーブ ", "已有数据": "データあり", "空": "空",
      "保存": "保存", "覆盖": "上書き", "读取": "読み込み", "删除": "削除", "Tyranor 修改器": "Tyranor 改造パネル", "版本 ": "バージョン ",
      "修改器只操作当前 RGSS 游戏的运行时对象。部分深度定制脚本可能覆盖标准引擎 API；遇到异常时可在单游戏设置中关闭修改器。": "このパネルは現在の RGSS ゲームの実行中データを変更します。独自スクリプトが標準 API を上書きする場合があります。エラーが発生した場合は、そのゲームの設定で無効にしてください。",
      "事件加速和对话快进可能跳过演出或等待，建议先保存游戏。": "イベント高速化とメッセージ早送りは演出や待機を飛ばす場合があります。先にセーブしてください。",
      "虚拟按键": "仮想ボタン", "按键层由应用原生实现，可自定义按键的位置、文字、大小与输出键位。进入编辑后：": "仮想ボタンはアプリのネイティブ機能です。位置、ラベル、サイズ、出力キーを編集できます：",
      "点按按键选中，拖拽调整位置": "ボタンをタップして選択し、ドラッグして移動", "右侧面板改文字 / 尺寸 / 键位": "右側のパネルでラベル / サイズ / キーを変更",
      "可复制、删除按键，或恢复默认": "ボタンの複製・削除、初期化が可能", "保存后立即生效": "保存後すぐに反映",
      "进入按键编辑": "ボタンを編集", "恢复默认": "初期設定に戻す", "当前未启用虚拟按键。可在应用的引擎设置中开启。": "仮想ボタンは無効です。アプリのエンジン設定で有効にできます。",
      "设置已保存": "設定を保存しました", "金币已修改": "所持金を変更しました", "金币已拉满": "所持金を最大にしました", "队伍已恢复": "パーティを回復しました",
      "数量已修改": "数量を変更しました", "角色已恢复": "キャラクターを回復しました", "队伍成员已更新": "パーティを更新しました", "属性已修改": "能力値を変更しました", "装备已更换": "装備を変更しました", "装备已卸下": "装備を外しました",
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
    var eventPage = text.match(/^事件页\s*(\d+)$/);
    if (eventPage) return language === "ja" ? "ページ " + eventPage[1] : "Page " + eventPage[1];
    var eventCounts = text.match(/^指令数\s*(\d+)\s*·\s*条件数\s*(\d+)$/);
    if (eventCounts) return language === "ja"
      ? "コマンド " + eventCounts[1] + " · 条件 " + eventCounts[2]
      : "Cmd " + eventCounts[1] + " · Cond " + eventCounts[2];
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
