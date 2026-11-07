# TYN RGSS runtime translation bridge. Kept Ruby 1.8 compatible for RGSS1/2.
module TyranorText
  SESSION_DIR = File.dirname(__FILE__)
  TABLE_PATH = File.join(SESSION_DIR, 'runtime_translations.hex')
  VERSION_PATH = File.join(SESSION_DIR, 'rgss_version')
  COPY_PATH = File.join(SESSION_DIR, 'tyranor_copy_text.txt')
  FAST_FORWARD_PATH = File.join(SESSION_DIR, 'fast_forward')
  @translations = {}
  @signature = nil
  @ticks = 0
  @active_message_sources = []
  @fast_forward_state = nil

  def self.log(message)
    begin
      File.open(File.join(SESSION_DIR, 'tyranor_runtime.log'), 'a') do |file|
        file.puts("[translation] #{message}")
      end
    rescue Exception
    end
  end

  def self.rgss_version
    return @rgss_version if @rgss_version
    begin
      @rgss_version = File.read(VERSION_PATH).to_i
    rescue Exception
      @rgss_version = 3
    end
    @rgss_version
  end

  def self.translate(value)
    return value unless value.is_a?(String) && !value.empty?
    @translations[value] || value
  rescue Exception
    value
  end

  def self.reload
    return unless File.file?(TABLE_PATH)
    stat = File.stat(TABLE_PATH)
    signature = [stat.mtime.to_f, stat.size]
    return if signature == @signature
    updated = {}
    File.open(TABLE_PATH, 'r') do |file|
      file.each_line do |line|
        fields = line.chomp.split("\t")
        next if fields.length < 2
        source = [fields[0]].pack('H*')
        target = [fields[1]].pack('H*')
        if fields.length >= 3 && target.respond_to?(:force_encoding)
          begin
            target.force_encoding(fields[2])
          rescue Exception
          end
        end
        updated[source] = target unless source.empty? || target.empty?
      end
    end
    @translations = updated
    @signature = signature
    log("loaded #{updated.length} translation entries")
    refresh_active_message
    refresh_windows
    apply_window_title
  rescue Exception => error
    log("translation reload failed: #{error}")
  end

  def self.set_active_message_sources(lines)
    @active_message_sources = lines.is_a?(Array) ? lines.collect { |line| line.is_a?(String) ? line.dup : '' } : []
    write_copy_text(@active_message_sources.collect { |line| translate(line) }.join("\n"))
  end

  def self.write_copy_text(value)
    return unless defined?(CFG) && CFG["copyText"]
    File.open(COPY_PATH, 'w') { |file| file.write(value.to_s) }
  rescue Exception => error
    log("copy text update failed: #{error}")
  end

  def self.update_fast_forward
    return unless defined?(Graphics) && Graphics.respond_to?(:set_fast_forward_speed)
    active = false
    begin
      active = File.file?(FAST_FORWARD_PATH) && File.read(FAST_FORWARD_PATH).strip == '1'
    rescue Exception
    end
    return if active == @fast_forward_state
    factor = active ? (CFG["speedUp"].to_i rescue 1) : 1
    factor = [[factor, 1].max, 9].min
    Graphics.set_fast_forward_speed(factor)
    @fast_forward_state = active
  rescue Exception => error
    log("fast-forward update failed: #{error}")
  end

  def self.refresh_active_message
    return if @active_message_sources.empty?
    lines = @active_message_sources.collect { |line| translate(line) }
    version = rgss_version
    if version >= 3 && defined?($game_message) && $game_message
      if $game_message.instance_variable_defined?(:@texts)
        $game_message.instance_variable_set(:@texts, lines.dup)
      elsif $game_message.respond_to?(:texts=)
        $game_message.texts = lines.dup
      end
      windows = message_windows
      windows.each do |window|
        window.instance_variable_set(:@fiber, nil) if window.instance_variable_defined?(:@fiber)
        window.instance_variable_set(:@line_count, 0) if window.instance_variable_defined?(:@line_count)
      end
      write_copy_text(lines.join("\n"))
      return
    end

    if version == 2 && defined?($game_message) && $game_message
      if $game_message.instance_variable_defined?(:@texts)
        $game_message.instance_variable_set(:@texts, lines.dup)
      elsif $game_message.respond_to?(:texts=)
        $game_message.texts = lines.dup
      end
      if defined?($game_temp) && $game_temp && $game_temp.respond_to?(:message_text=)
        $game_temp.message_text = lines.join("\n")
      end
      message_windows.each do |window|
        begin
          window.send(:start_message) if window.respond_to?(:start_message, true)
        rescue Exception => error
          log("VX message redraw unavailable: #{error}")
        end
      end
      write_copy_text(lines.join("\n"))
      return
    end

    if defined?($game_temp) && $game_temp && $game_temp.respond_to?(:message_text=)
      $game_temp.message_text = lines.join("\n")
    end
    message_windows.each do |window|
      begin
        window.refresh if window.respond_to?(:refresh)
      rescue Exception
      end
    end
    write_copy_text(lines.join("\n"))
  rescue Exception => error
    log("active message refresh failed: #{error}")
  end

  def self.message_windows
    return [] unless defined?(Window) && defined?(ObjectSpace)
    result = []
    ObjectSpace.each_object(Window) do |window|
      result << window if window.class.to_s =~ /Window_Message/
    end
    result
  rescue Exception
    []
  end

  def self.refresh_windows
    return unless defined?(Window) && defined?(ObjectSpace)
    ObjectSpace.each_object(Window) do |window|
      next if window.class.to_s =~ /Window_Message/
      next unless window.respond_to?(:refresh)
      begin
        window.refresh
      rescue Exception
      end
    end
  rescue Exception
  end

  def self.apply_window_title
    return unless defined?(System) && System.respond_to?(:set_window_title)
    title = nil
    if defined?($data_system) && $data_system && $data_system.respond_to?(:game_title)
      title = $data_system.game_title
    end
    title = translate(title) if title.is_a?(String)
    System.set_window_title(title) if title.is_a?(String) && !title.empty?
  rescue Exception
  end

  def self.install_bitmap_hook
    if defined?(System) && System.respond_to?(:native_bitmap_text_translation?) && System.native_bitmap_text_translation?
      unless @native_bitmap_hook_logged
        log('Bitmap text translation handled by native binding')
        @native_bitmap_hook_logged = true
      end
      return
    end
    return unless defined?(Bitmap) && Bitmap.method_defined?(:draw_text)
    return if Bitmap.method_defined?(:__tyranor_draw_text)
    Bitmap.class_eval do
      alias __tyranor_draw_text draw_text
      def draw_text(*args)
        translated = args.dup
        if translated[0].is_a?(String)
          translated[0] = TyranorText.translate(translated[0])
        elsif translated[1].is_a?(String)
          translated[1] = TyranorText.translate(translated[1])
        end
        __tyranor_draw_text(*translated)
      end
    end
  rescue Exception => error
    log("Bitmap hook failed: #{error}")
  end

  def self.install_window_hook
    return if defined?(System) && System.respond_to?(:native_bitmap_text_translation?) && System.native_bitmap_text_translation?
    return unless defined?(Window_Base) && Window_Base.method_defined?(:draw_text)
    return if Window_Base.method_defined?(:__tyranor_draw_text)
    Window_Base.class_eval do
      alias __tyranor_draw_text draw_text
      def draw_text(*args)
        translated = args.dup
        if translated[0].is_a?(String)
          translated[0] = TyranorText.translate(translated[0])
        elsif translated[1].is_a?(String)
          translated[1] = TyranorText.translate(translated[1])
        end
        __tyranor_draw_text(*translated)
      end
    end
  rescue Exception => error
    log("Window_Base hook failed: #{error}")
  end

  def self.install_interpreter_hook
    klass = if defined?(Game_Interpreter)
      Game_Interpreter
    elsif defined?(Interpreter)
      Interpreter
    end
    return unless klass && (klass.method_defined?(:execute_command) || klass.private_method_defined?(:execute_command))
    return if klass.method_defined?(:__tyranor_execute_command) || klass.private_method_defined?(:__tyranor_execute_command)
    private_method = klass.private_method_defined?(:execute_command)
    klass.class_eval do
      alias __tyranor_execute_command execute_command
      def execute_command(*args)
        changed = []
        command = @list[@index] if @list.is_a?(Array) && @index.is_a?(Integer) && @index >= 0 && @index < @list.length
        code = command.code if command && command.respond_to?(:code)
        parameters = command.parameters if command && command.respond_to?(:parameters)
        if parameters.is_a?(Array)
          case code
          when 101, 105
            lines = []
            line_code = code == 101 ? 401 : 405
            line_index = @index + 1
            while @list && line_index < @list.length
              line_command = @list[line_index]
              break unless line_command && line_command.respond_to?(:code) && line_command.code == line_code
              line_parameters = line_command.parameters if line_command.respond_to?(:parameters)
              if line_parameters.is_a?(Array) && line_parameters[0].is_a?(String)
                original = line_parameters[0]
                lines << original if code == 101
                line_parameters[0] = TyranorText.translate(original)
                changed << [line_parameters, 0, original]
              end
              line_index += 1
            end
            TyranorText.set_active_message_sources(lines) if code == 101
          when 401, 405
            if parameters[0].is_a?(String)
              original = parameters[0]
              parameters[0] = TyranorText.translate(original)
              changed << [parameters, 0, original]
            end
          when 102
            if parameters[0].is_a?(Array)
              original = parameters[0]
              parameters[0] = original.collect { |text| TyranorText.translate(text) }
              changed << [parameters, 0, original]
            end
          when 320, 324, 325
            if parameters[1].is_a?(String)
              original = parameters[1]
              parameters[1] = TyranorText.translate(original)
              changed << [parameters, 1, original]
            end
          end
        end
        __tyranor_execute_command(*args)
      ensure
        changed.each { |target, index, original| target[index] = original if target.is_a?(Array) }
      end
    end
    klass.send(:private, :execute_command) if private_method
  rescue Exception => error
    TyranorText.log("interpreter hook failed: #{error}")
  end

  def self.install_hooks
    install_bitmap_hook
    install_window_hook
    install_interpreter_hook
  end

  def self.tick
    @ticks += 1
    return unless (@ticks % 10) == 0
    update_fast_forward
    reload
    install_hooks
  rescue Exception
  end
end

TyranorText.reload
TyranorText.install_hooks

if defined?(Graphics) && Graphics.respond_to?(:update) && !Graphics.respond_to?(:__tyranor_update)
  class << Graphics
    alias __tyranor_update update
    def update(*args)
      result = __tyranor_update(*args)
      TyranorText.tick
      result
    end
  end
end
