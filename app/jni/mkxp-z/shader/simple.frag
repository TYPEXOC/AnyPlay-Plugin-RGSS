
uniform sampler2D texture;

#ifdef GLSLES
#ifdef GL_FRAGMENT_PRECISION_HIGH
varying highp vec2 v_texCoord;
#else
varying mediump vec2 v_texCoord;
#endif
#else
varying vec2 v_texCoord;
#endif

void main()
{
	gl_FragColor = texture2D(texture, v_texCoord);
}
