# mkxp_wrap.rb
# Author: Splendide Imaginarius (2022)

# Creative Commons CC0: To the extent possible under law, Splendide Imaginarius
# has waived all copyright and related or neighboring rights to mkxp_wrap.rb.
# https://creativecommons.org/publicdomain/zero/1.0/

# This preload script provides functions that existed in Ancurio's mkxp, but
# were renamed in mkxp-z, so that games (or other preload scripts) that expect
# Ancurio's function names can find them.

module MKXP
	class << self
		def data_directory(*args)
			System::data_directory(*args)
		end

		def puts(*args)
			System::puts(*args)
		end

		def raw_key_states(*args)
			states = Input::raw_key_states(*args)
			class << states
				def getbyte(byte)
					self[byte] ? 1 : 0
				end
				def setbyte(byte, val)
					self[byte] = val == 0 ? false : true
				end
			end
			states
		end

		def mouse_in_window(*args)
			Input::mouse_in_window(*args)
		end
	end
end

# Some RGSS games call this positional SE helper, while mkxp-z exposes only
# Audio.se_play. Keep the filename, volume, and pitch; this backend has no
# positional/panning API for SE playback, so the coordinates are ignored.
unless Audio.respond_to?(:se_play_position)
	def Audio.se_play_position(filename, volume = 100, pitch = 100, _x = 0, _y = 0, _z = 0)
		Audio.se_play(filename, volume, pitch)
	end
end

# Some Essentials-based games call this helper once per map update, although
# mkxp-z advances autotile animation from each native Tilemap#update call.
# Keep the optional game hook available without replacing native tilemap work.
unless defined?(TilemapUtils) && TilemapUtils.respond_to?(:updateAutoTiles)
	unless defined?(TilemapUtils)
		module TilemapUtils
		end
	end
	def TilemapUtils.updateAutoTiles
		nil
	end
	MKXP.puts("[TYN RGSS] TilemapUtils.updateAutoTiles fallback active; native Tilemap#update handles autotile animation") if MKXP.respond_to?(:puts)
end

# Windows text-mode reads translate CRLF to LF. Android does not perform that
# conversion, so normalize common text-file reads to preserve the behavior
# expected by scripts exported on Windows (for example, a CSV parsed with
# split("\n")). Paths with non-text extensions remain unchanged.
class IO
	unless method_defined?(:tyn_android_text_read_original)
		alias_method :tyn_android_text_read_original, :read

		def read(*arguments)
			content = tyn_android_text_read_original(*arguments)
			if content.is_a?(String)
				begin
					file_path = path.to_s
					if file_path =~ /\.(?:csv|txt|ini|cfg|yaml|yml|json)$/i
						if content.gsub!("\r\n", "\n") && !$tyn_android_text_newlines_logged
							$tyn_android_text_newlines_logged = true
							MKXP.puts("[TYN RGSS] normalized Windows CRLF text while reading #{file_path}") if defined?(MKXP) && MKXP.respond_to?(:puts)
						end
					end
				rescue Exception
				end
			end
			content
		end
	end
end
