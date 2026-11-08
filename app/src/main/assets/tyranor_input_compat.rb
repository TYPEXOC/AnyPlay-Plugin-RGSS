# Keep mkxp-z's SDL input update active when an RGSS game replaces Input.update.
# This is needed by games whose custom Win32 keyboard-state layer owns Input.update.
module TyranorInputCompat
	def self.log(message)
		return unless ENV["TYN_RGSS_VERBOSE_LOG"] == "1"
		System.puts("[TYNInputCompat] #{message}")
	rescue Exception
	end

	def self.capture_native_input_update
		return unless defined?(Input) && Input.respond_to?(:update)
		singleton = class << Input; self; end
		return if singleton.method_defined?(:tyranor_native_input_update)
		singleton.send(:alias_method, :tyranor_native_input_update, :update)
		@native_input_update = Input.method(:tyranor_native_input_update)
		log("captured mkxp-z Input.update")
	rescue Exception => error
		log("could not capture native Input.update: #{error.class}: #{error}")
	end

	def self.install_game_input_bridge
		return unless @native_input_update
		return unless defined?(Input) && Input.respond_to?(:update)
		return unless Input.const_defined?(:GetKeyboardState)

		current = Input.method(:update)
		return if current == @native_input_update || @wrapped_input_update == current

		singleton = class << Input; self; end
		@wrapper_generation = (@wrapper_generation || 0) + 1
		game_method = "tyranor_game_input_update_#{@wrapper_generation}".to_sym
		singleton.send(:alias_method, game_method, :update)
		singleton.send(:define_method, :update) do |*arguments|
			tyranor_native_input_update
			send(game_method, *arguments)
		end
		@wrapped_input_update = Input.method(:update)
		log("chained mkxp-z Input.update before game GetKeyboardState handler")
	rescue Exception => error
		log("could not chain game Input.update: #{error.class}: #{error}")
	end
end

TyranorInputCompat.capture_native_input_update

class << Graphics
	alias_method :tyranor_input_compat_graphics_update, :update
	def update(*arguments)
		result = tyranor_input_compat_graphics_update(*arguments)
		TyranorInputCompat.install_game_input_bridge
		result
	end
end
