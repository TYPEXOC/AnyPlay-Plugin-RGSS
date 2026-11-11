# Optional in-game cheat window for the Android RGSS module.
# This file is loaded after the game script pack. It defers class hooking until
# the first live frame because game script classes may not have executed yet.
# Keep the syntax compatible with Ruby 1.8.
begin
  if defined?(TyranorCheats)
    module TyranorCheats
      @native_window_requested = false
      @native_window_close_requested = false
      @native_window = nil
      @native_window_tab = 0
      @native_window_index = 0
      @native_window_offset = 0
      @native_window_message = ""
      @native_window_item_kind = 0
      @native_window_rows = nil
      @native_window_rows_dirty = true
      @native_window_dirty = true
      @native_window_refresh_tick = 0

      def self.native_window_write_state(value)
        File.open(WINDOW_STATE, "wb") { |file| file.write(value.to_s) }
      rescue Exception
      end

      def self.open_native_window
        return "The RGSS window isn't available in this game scene." unless defined?(Scene_Map) && defined?(Window_Base)
        scene = (defined?(SceneManager) && SceneManager.respond_to?(:scene) ? SceneManager.scene : nil)
        scene = $scene if !scene && defined?($scene)
        return "Open the RGSS window from the map scene." unless scene && scene.is_a?(Scene_Map)
        @native_window_requested = true
        @native_window_close_requested = false
        @native_window_message = ""
        native_window_write_state("1")
        "RGSS cheat window requested"
      rescue Exception => error
        "Couldn't open the RGSS window: #{error.class}: #{error.message}"
      end

      def self.close_native_window
        @native_window_requested = false
        @native_window_close_requested = true
        native_window_write_state("0")
        "RGSS cheat window closing"
      rescue Exception => error
        "Couldn't close the RGSS window: #{error.class}: #{error.message}"
      end

      def self.install_native_window_hook
        return if @native_window_hooked
        return unless defined?(Scene_Map) && class_method?(Scene_Map, :update)
        Scene_Map.class_eval do
          alias_method :__tyranor_native_cheat_update, :update
          def update(*args)
            if TyranorCheats.native_window_update(self)
              return
            end
            __tyranor_native_cheat_update(*args)
          end
        end
        @native_window_hooked = true
        AnyPlayLog.write("RGSS native cheat window hook installed") rescue nil
      rescue Exception => error
        AnyPlayLog.error("RGSS native cheat window hook failed: #{error.class}: #{error.message}") rescue nil
      end

      def self.native_window_update(scene)
        return false unless @native_window || @native_window_requested || @native_window_close_requested
        if @native_window_close_requested
          native_window_dispose
          return true
        end
        if @native_window_requested && !@native_window
          native_window_create
        end
        return true unless @native_window
        @native_window_refresh_tick += 1
        if (@native_window_refresh_tick % 45) == 0
          @native_window_rows_dirty = true
          @native_window_dirty = true
        end
        @native_window.update if @native_window.respond_to?(:update)
        native_window_handle_input
        native_window_draw if @native_window && @native_window_dirty
        true
      rescue Exception => error
        AnyPlayLog.error("RGSS native cheat window update failed: #{error.class}: #{error.message}\n  #{(error.backtrace || []).first(6).join("\n  ")}") rescue nil
        native_window_dispose
        true
      end

      def self.native_window_create
        screen_width = (Graphics.width rescue 640).to_i
        screen_height = (Graphics.height rescue 480).to_i
        width = [[(screen_width * 0.90).to_i, 300].max, screen_width].min
        height = [[(screen_height * 0.84).to_i, 220].max, screen_height].min
        x = (screen_width - width) / 2
        y = (screen_height - height) / 2
        @native_window = Window_Base.new(x, y, width, height)
        @native_window.opacity = 245 if @native_window.respond_to?(:opacity=)
        @native_window.back_opacity = 225 if @native_window.respond_to?(:back_opacity=)
        @native_window.z = 9999 if @native_window.respond_to?(:z=)
        @native_window_requested = false
        @native_window_close_requested = false
        @native_window_tab = 0
        @native_window_index = 0
        @native_window_offset = 0
        @native_window_item_kind = 0
        @native_window_rows = nil
        @native_window_rows_dirty = true
        @native_window_dirty = true
        @native_window_refresh_tick = 0
        @native_window_message = ""
        native_window_draw
        AnyPlayLog.write("RGSS native cheat window opened engine=#{engine}") rescue nil
      end

      def self.native_window_dispose
        window = @native_window
        @native_window = nil
        @native_window_rows = nil
        @native_window_rows_dirty = true
        @native_window_dirty = true
        @native_window_requested = false
        @native_window_close_requested = false
        window.dispose if window && window.respond_to?(:dispose) && !(window.respond_to?(:disposed?) && window.disposed?)
        native_window_write_state("0")
        AnyPlayLog.write("RGSS native cheat window closed") rescue nil
      rescue Exception => error
        AnyPlayLog.error("RGSS native cheat window dispose failed: #{error.class}: #{error.message}") rescue nil
      end

      def self.native_window_tabs
        [["快捷", :quick], ["物品", :items], ["角色", :actors], ["系统", :system],
         ["地图", :map], ["战斗", :battle], ["存档", :saves]]
      end

      def self.native_window_rows
        case native_window_tabs[@native_window_tab][1]
        when :quick then native_window_quick_rows
        when :items then native_window_item_rows
        when :actors then native_window_actor_rows
        when :system then native_window_system_rows
        when :map then native_window_map_rows
        when :battle then native_window_battle_rows
        when :saves then native_window_save_rows
        else [] end
      rescue Exception => error
        [["读取数据失败：#{error.class}: #{error.message}", :note]]
      end

      def self.native_window_cached_rows
        if @native_window_rows_dirty || @native_window_rows.nil?
          @native_window_rows = native_window_rows
          @native_window_rows_dirty = false
        end
        @native_window_rows
      end

      def self.native_window_quick_rows
        @native_window_help = "Enter 切换开关或执行操作；PageUp/PageDown 切换分类"
        rows = []
        flags = [["无敌", "invincible"], ["一击必杀", "oneHit"], ["必定暴击", "crit"],
                 ["穿墙", "noclip"], ["禁止遇敌", "noEncounter"], ["事件加速", "eventSpeed"],
                 ["对话快进", "msgSkip"], ["HP/MP 不减", "hpmp"], ["金币不减", "gold"],
                 ["物品不减", "items"], ["移动加速", "speed"]]
        flags.each do |label, name|
          next unless toggle_supported?(name)
          rows << ["#{label}：#{on?(name) ? '开' : '关'}", :toggle, name]
        end
        rows << ["全队恢复", :run, "recoverParty"]
        rows << ["物品补至 99", :run, "fullInventory"]
        rows << ["金币最大", :run, "maxGold"]
        rows << ["队伍等级设为 99", :value, "level"]
        rows
      end

      def self.native_window_item_rows
        rows = []
        kinds = ess? ? ["item"] : ["item", "weapon", "armor"]
        kind = kinds[@native_window_item_kind % kinds.length]
        @native_window_help = "Enter 在类别行切换物品/武器/护甲；Enter +1，← -1，→ +10"
        rows << ["当前类别：#{kind == 'item' ? '物品' : (kind == 'weapon' ? '武器' : '护甲')}（Enter 切换）", :item_kind]
        if ess?
          if gd?
            GameData::Item.each do |item|
              name = item.respond_to?(:name) ? item.name.to_s : ""
              key = item.respond_to?(:id) ? item.id : nil
              next if key.nil? || name.empty?
              rows << ["#{name}  x#{quantity_of('item', key)}", :item, "item", key]
            end
          elsif defined?(PBItems) && PBItems.respond_to?(:maxValue)
            (1..(PBItems.maxValue rescue 0)).each do |id|
              name = (PBItems.getName(id) rescue "").to_s
              rows << ["#{name}  x#{quantity_of('item', id)}", :item, "item", id] unless name.empty?
            end
          end
        else
          table = table_for(kind)
          (1...(table ? table.size : 1)).each do |id|
            item = table[id]
            next unless item && item.respond_to?(:name) && !item.name.to_s.empty?
            rows << ["##{id} #{item.name}  x#{quantity_of(kind, id)}", :item, kind, id]
          end
        end
        rows = [["没有可用物品数据", :note]] if rows.empty?
        rows
      end

      def self.native_window_actor_rows
        rows = []
        if ess?
          pokes.each_with_index do |pokemon, index|
            next unless pokemon
            name = pokemon_display_name(pokemon)
            level = pokemon.respond_to?(:level) ? pokemon.level.to_i : 0
            hp = pokemon.respond_to?(:hp) ? pokemon.hp.to_i : 0
            max_hp = pokemon.respond_to?(:totalhp) ? pokemon.totalhp.to_i : hp
            suffix = pokemon.respond_to?(:egg?) && pokemon.egg? ? "  (蛋)" : ""
            rows << ["#{name} Lv#{level}  HP #{hp}/#{max_hp}#{suffix}", :pokemon, index]
          end
        @native_window_help = "Enter 恢复宝可梦，←/→ 调整等级"
        else
          actors.each_with_index do |actor, index|
            id = actor_id_of(actor, index)
            name = actor.respond_to?(:name) ? actor.name.to_s : "角色 #{id}"
            level = actor.respond_to?(:level) ? actor.level.to_i : 0
            hp = actor.respond_to?(:hp) ? actor.hp.to_i : 0
            max_hp = actor.respond_to?(:mhp) ? actor.mhp.to_i : (actor.respond_to?(:maxhp) ? actor.maxhp.to_i : hp)
            rows << ["#{name} Lv#{level}  HP #{hp}/#{max_hp}", :actor, id]
          end
        @native_window_help = "Enter 恢复角色，←/→ 调整等级"
        end
        rows = [["当前队伍没有角色", :note]] if rows.empty?
        rows
      end

      def self.native_window_system_rows
        rows = []
        return [["请先进入游戏存档", :note]] unless $game_switches && $game_variables
        database = system_database
        switch_names = system_names(database, :switches) || []
        variable_names = system_names(database, :variables) || []
        switch_limit = switch_names.size
        (1...switch_limit).each do |id|
          name = switch_names[id].to_s
          next if name.empty?
          value = ($game_switches[id] rescue false) ? "开" : "关"
          rows << ["开关 ##{id} #{name}：#{value}", :switch, id]
        end
        variable_limit = variable_names.size
        (1...variable_limit).each do |id|
          name = variable_names[id].to_s
          next if name.empty?
          value = ($game_variables[id] rescue nil)
          rows << ["变量 ##{id} #{name}：#{value}", :variable, id]
        end
        @native_window_help = "开关 Enter 切换；变量 ←/→ -/+1"
        rows = [["游戏没有命名的开关或变量", :note]] if rows.empty?
        rows
      end

      def self.native_window_map_rows
        current = ($game_map && $game_map.respond_to?(:map_id)) ? $game_map.map_id.to_i : 0
        rows = map_info_entries.map do |entry|
          id, name = entry
          here = id.to_i == current
          ["#{here ? '▶ ' : ''}##{id} #{name}#{here ? '（当前位置）' : ''}", :map, id]
        end
        @native_window_help = "Enter 传送到选中地图的安全位置"
        rows = [["地图列表不可用", :note]] if rows.empty?
        rows
      end

      def self.native_window_battle_rows
        rows = []
        rows << ["当前不在战斗中", :note] unless in_battle?
        rows << ["击败全部敌人", :run, "killEnemies"]
        rows << ["强制胜利", :run, "forceVictory"]
        rows << ["强制逃跑", :run, "forceEscape"]
        @native_window_help = "Enter 执行选中的战斗操作"
        rows
      end

      def self.native_window_save_rows
        rows = []
        raw = list_save_slots.to_s
        raw.each_line do |line|
          parts = line.chomp.split("\t", 7)
          next unless parts.length >= 6 && parts[0] == "S"
          id = parts[1].to_i
          exists = parts[2] == "1"
          current = parts[3] == "1"
          name = parts[5].to_s
          rows << ["#{current ? '▶ ' : ''}##{id} #{name}#{exists ? '' : '（空）'}", :save, id, exists]
        end
        @native_window_help = "Enter 保存，→ 读取已有存档"
        rows = [["存档接口在此游戏中不可用", :note]] if rows.empty?
        rows
      end

      def self.native_window_input?(name, repeat)
        return false unless defined?(Input) && Input.respond_to?(:trigger?)
        return false unless Input.const_defined?(name)
        code = Input.const_get(name)
        repeat && Input.respond_to?(:repeat?) ? Input.repeat?(code) : Input.trigger?(code)
      rescue Exception
        false
      end

      def self.native_window_handle_input
        if native_window_input?(:B, false)
          close_native_window
          return
        end
        rows = native_window_cached_rows
        @native_window_index = 0 if rows.empty?
        @native_window_index = [[@native_window_index, 0].max, rows.length - 1].min unless rows.empty?
        if native_window_input?(:UP, true)
          @native_window_index -= 1
          @native_window_dirty = true
        elsif native_window_input?(:DOWN, true)
          @native_window_index += 1
          @native_window_dirty = true
        end
        @native_window_index = [[@native_window_index, 0].max, rows.length - 1].min unless rows.empty?
        if native_window_input?(:L, false)
          @native_window_tab = (@native_window_tab - 1) % native_window_tabs.length
          @native_window_index = 0
          @native_window_message = ""
          @native_window_rows_dirty = true
          @native_window_dirty = true
        elsif native_window_input?(:R, false)
          @native_window_tab = (@native_window_tab + 1) % native_window_tabs.length
          @native_window_index = 0
          @native_window_message = ""
          @native_window_rows_dirty = true
          @native_window_dirty = true
        end
        tab = native_window_tabs[@native_window_tab][1]
        rows = native_window_cached_rows
        if tab == :items && native_window_input?(:LEFT, true)
          native_window_apply(rows[@native_window_index], :left)
        elsif tab == :items && native_window_input?(:RIGHT, true)
          native_window_apply(rows[@native_window_index], :right)
        elsif tab == :actors && native_window_input?(:LEFT, true)
          native_window_apply(rows[@native_window_index], :left)
        elsif tab == :actors && native_window_input?(:RIGHT, true)
          native_window_apply(rows[@native_window_index], :right)
        elsif tab == :system && native_window_input?(:LEFT, true)
          native_window_apply(rows[@native_window_index], :left)
        elsif tab == :system && native_window_input?(:RIGHT, true)
          native_window_apply(rows[@native_window_index], :right)
        elsif tab == :saves && native_window_input?(:RIGHT, false)
          native_window_apply(rows[@native_window_index], :right)
        elsif native_window_input?(:C, false)
          native_window_apply(rows[@native_window_index], :confirm)
        end
        native_window_mouse_input(rows)
      end

      def self.native_window_mouse_input(rows)
        return unless native_window_input?(:MOUSELEFT, false)
        return unless Input.respond_to?(:mouse_x) && Input.respond_to?(:mouse_y)
        x = Input.mouse_x.to_i
        y = Input.mouse_y.to_i
        window = @native_window
        return unless window && x >= window.x && y >= window.y && x < window.x + window.width && y < window.y + window.height
        local_x = x - window.x
        local_y = y - window.y
        padding = window.respond_to?(:padding) ? window.padding.to_i : 16
        content_x = local_x - padding
        content_y = local_y - padding
        if content_y < 36
          close_native_window if content_x > window.contents.width - 56
          return
        end
        if content_y < 76
          tab_width = window.contents.width / native_window_tabs.length
          picked = [[content_x / [tab_width, 1].max, 0].max, native_window_tabs.length - 1].min
          if picked != @native_window_tab
            @native_window_tab = picked
            @native_window_index = 0
            @native_window_message = ""
            @native_window_rows_dirty = true
            @native_window_dirty = true
          end
          return
        end
        row_height = 27
        row_index = @native_window_offset + ((content_y - 76) / row_height)
        if row_index >= 0 && row_index < rows.length
          @native_window_index = row_index
          @native_window_dirty = true
          native_window_apply(rows[row_index], :confirm)
        end
      rescue Exception
      end

      def self.native_window_apply(row, action)
        return unless row && row[1] != :note
        type = row[1]
        result = ""
        case type
        when :item_kind
          @native_window_item_kind = (@native_window_item_kind + 1) % (ess? ? 1 : 3)
          @native_window_index = 0
          @native_window_message = ""
          @native_window_rows_dirty = true
          @native_window_dirty = true
          return
        when :toggle
          result = toggle(row[2], !on?(row[2]))
        when :run
          result = run_action(row[2])
        when :value
          result = set_value(row[2], "99")
        when :item
          kind = row[2]
          key = row[3]
          quantity = quantity_of(kind, key).to_i
          delta = action == :left ? -1 : (action == :right ? 10 : 1)
          result = set_quantity(kind, "#{key}\t#{[quantity + delta, 0].max}")
        when :pokemon
          index = row[2]
          if action == :left || action == :right
            pokemon = pokes[index.to_i]
            level = pokemon && pokemon.respond_to?(:level) ? pokemon.level.to_i : 1
            result = set_pokemon_value(index.to_s, "level\t#{[level + (action == :left ? -1 : 1), 1].max}")
          else
            result = recover_pokemon(index.to_s)
          end
        when :actor
          id = row[2]
          if action == :left || action == :right
            actor = actor_by_id(id.to_s)
            level = actor && actor.respond_to?(:level) ? actor.level.to_i : 1
            result = set_actor_value(id.to_s, "level\t#{[level + (action == :left ? -1 : 1), 1].max}")
          else
            result = recover_actor(id.to_s)
          end
        when :switch
          id = row[2].to_i
          result = set_system_switch(id.to_s, ($game_switches[id] ? "0" : "1"))
        when :variable
          id = row[2].to_i
          value = ($game_variables[id] rescue 0)
          step = action == :left ? -1 : 1
          next_value = value.is_a?(Numeric) ? value + step : step
          result = set_system_variable(id.to_s, next_value.to_s)
        when :map
          result = warp_map(row[2].to_s, "")
          close_native_window if result == "ok"
        when :save
          id = row[2].to_i
          if action == :right
            result = row[3] ? load_slot(id.to_s) : "这个存档槽是空的"
            close_native_window if result.to_s.index("loaded")
          else
            result = save_slot(id.to_s)
          end
        end
        @native_window_message = result.to_s
        @native_window_rows_dirty = true
        @native_window_dirty = true
        write_state if [:toggle].include?(type)
      rescue Exception => error
        @native_window_message = "操作失败：#{error.class}: #{error.message}"
        @native_window_dirty = true
        AnyPlayLog.error("RGSS native cheat action failed: #{@native_window_message}") rescue nil
      end

      def self.native_window_draw
        window = @native_window
        return unless window && window.contents
        contents = window.contents
        contents.clear
        contents.font.size = 19 if contents.font.respond_to?(:size=)
        contents.font.color = Color.new(240, 245, 242)
        contents.draw_text(0, 0, contents.width - 44, 28, "RPG Maker 修改器 · RGSS", 0)
        contents.draw_text(contents.width - 42, 0, 38, 28, "×", 2)
        tab_y = 38
        tab_width = contents.width / native_window_tabs.length
        native_window_tabs.each_with_index do |entry, index|
          x = index * tab_width
          if index == @native_window_tab
            contents.fill_rect(x, tab_y, tab_width - 2, 28, Color.new(55, 130, 220, 155))
          end
          contents.font.color = index == @native_window_tab ? Color.new(255, 255, 255) : Color.new(174, 185, 180)
          contents.draw_text(x, tab_y, tab_width - 2, 28, entry[0], 1)
        end
        rows = native_window_cached_rows
        @native_window_index = 0 if rows.empty?
        @native_window_index = [[@native_window_index, 0].max, rows.length - 1].min unless rows.empty?
        row_top = 76
        row_height = 27
        footer_top = contents.height - 34
        visible_count = [[(footer_top - row_top) / row_height, 1].max, rows.length].min
        visible_count = 1 if rows.empty?
        desired_offset = [[@native_window_index - visible_count + 1, 0].max, 0].max
        @native_window_offset = desired_offset if @native_window_index < @native_window_offset
        @native_window_offset = @native_window_index - visible_count + 1 if @native_window_index >= @native_window_offset + visible_count
        @native_window_offset = 0 if @native_window_offset < 0
        @native_window_offset = [@native_window_offset, [rows.length - visible_count, 0].max].min
        visible_count.times do |visible_index|
          index = @native_window_offset + visible_index
          row = rows[index]
          y = row_top + visible_index * row_height
          if index == @native_window_index
            contents.fill_rect(0, y, contents.width, row_height, Color.new(55, 130, 220, 100))
            contents.font.color = Color.new(255, 255, 255)
          else
            contents.font.color = row && row[1] == :note ? Color.new(174, 185, 180) : Color.new(226, 232, 228)
          end
          contents.draw_text(8, y, contents.width - 16, row_height, row ? row[0].to_s : "", 0)
        end
        contents.font.size = 14 if contents.font.respond_to?(:size=)
        contents.font.color = Color.new(177, 190, 183)
        message = @native_window_message.to_s
        message = @native_window_help.to_s if message.empty?
        message = "↑↓选择  Enter确认  Esc关闭  PageUp/PageDown切换分类" if message.empty?
        contents.draw_text(0, footer_top, contents.width, 24, message, 0)
        @native_window_dirty = false
      rescue Exception => error
        AnyPlayLog.error("RGSS native cheat window draw failed: #{error.class}: #{error.message}") rescue nil
      end
    end

    AnyPlayLog.write("RGSS native cheat window layer loaded") rescue nil
  end
rescue Exception => error
  AnyPlayLog.error("RGSS native cheat window preload failed: #{error.class}: #{error.message}\n  #{(error.backtrace || []).first(6).join("\n  ")}") rescue nil
end
