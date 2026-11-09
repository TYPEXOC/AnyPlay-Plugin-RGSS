uniform sampler2D texture;

uniform lowp vec4 tone;

uniform lowp float opacity;
uniform lowp vec4 color;

/* The atlas can be thousands of pixels tall (big tilesets). A mediump
 * texcoord only has ~10 bits of precision, which picks the wrong tiles
 * on mobile GPUs, so ask for highp where the GPU has it. */
#ifdef GLSLES
#ifdef GL_FRAGMENT_PRECISION_HIGH
varying highp vec2 v_texCoord;
#else
varying mediump vec2 v_texCoord;
#endif
#else
varying vec2 v_texCoord;
#endif

const vec3 lumaF = vec3(.299, .587, .114);

void main() {
  /* Sample source color */
  vec4 frag = texture2D(texture, v_texCoord);

  /* Apply gray */
  float luma = dot(frag.rgb, lumaF);
  frag.rgb = mix(frag.rgb, vec3(luma), tone.w);

  /* Apply tone */
  frag.rgb += tone.rgb;

  /* Apply opacity */
  frag.a *= opacity;

  /* Apply color */
  frag.rgb = mix(frag.rgb, color.rgb, color.a);

  gl_FragColor = frag;
}
