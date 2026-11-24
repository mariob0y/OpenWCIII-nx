package com.etheller.warsmash.viewer5.gl;

import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;

/**
 * warsmash-nx: a GL30 in front of the real one that drops calls which only set a state to the value it
 * already has: uniforms (per program and location, compared bit for bit), glEnable/glDisable,
 * glDepthMask and the blend function. Everything else goes straight through. The model renderer sets
 * every uniform and state for every draw; on OrcX01 47% of the GL calls of a frame were such repeats
 * (2740 of 5786, counted on the PC with NX_GLCOUNT), and on the console every GL call crosses into
 * native code and Mesa's GL-to-Vulkan layer (zink), whose driver thread runs each one.
 *
 * Correct as long as every call that changes these states goes through this object (Gdx.gl, gl20 and
 * gl30 point at it from the start of the game) and nothing else changes them: uniform values belong to
 * a program and only change with glUniform* and glLinkProgram; the capabilities, depth mask and blend
 * function only change with their own calls. A uniform form the cache does not compare (unsigned,
 * non-square matrices, transposed matrices) forgets that location's value. Turn it off with the
 * environment variable NX_GLCACHE=0 (PC tests).
 *
 * Generated from the GL20/GL30 interfaces of gdx 1.12.1 / 1.13.1 (identical).
 */
public final class StateCachingGL30 implements GL30 {
	private final GL30 gl;
	private int program;
	/** program -> location -> {kind, count, value bits...} */
	private final IntMap<IntMap<int[]>> uniforms = new IntMap<>();
	private final IntIntMap caps = new IntIntMap();
	private int depthMask = -1;
	private int blendSrcRgb = -1, blendDstRgb = -1, blendSrcAlpha = -1, blendDstAlpha = -1;
	private final int[] scratch = new int[4];

	public StateCachingGL30(final GL30 gl) {
		this.gl = gl;
	}

	public GL30 getDelegate() {
		return this.gl;
	}

	/** Installs the cache as Gdx.gl, gl20 and gl30 unless NX_GLCACHE=0 or there is no GL30. */
	public static void install() {
		if ("0".equals(System.getenv("NX_GLCACHE")) || (com.badlogic.gdx.Gdx.gl30 == null)
				|| (com.badlogic.gdx.Gdx.gl30 instanceof StateCachingGL30)) {
			return;
		}
		final StateCachingGL30 caching = new StateCachingGL30(com.badlogic.gdx.Gdx.gl30);
		com.badlogic.gdx.Gdx.gl = caching;
		com.badlogic.gdx.Gdx.gl20 = caching;
		com.badlogic.gdx.Gdx.gl30 = caching;
		System.out.println("GL state cache on");
	}

	private boolean setCap(final int cap, final int value) {
		if (this.caps.get(cap, -1) == value) {
			return false;
		}
		this.caps.put(cap, value);
		return true;
	}

	private boolean setBlend(final int srcRgb, final int dstRgb, final int srcAlpha, final int dstAlpha) {
		if ((this.blendSrcRgb == srcRgb) && (this.blendDstRgb == dstRgb) && (this.blendSrcAlpha == srcAlpha)
				&& (this.blendDstAlpha == dstAlpha)) {
			return false;
		}
		this.blendSrcRgb = srcRgb;
		this.blendDstRgb = dstRgb;
		this.blendSrcAlpha = srcAlpha;
		this.blendDstAlpha = dstAlpha;
		return true;
	}

	private IntMap<int[]> programUniforms() {
		IntMap<int[]> values = this.uniforms.get(this.program);
		if (values == null) {
			values = new IntMap<>();
			this.uniforms.put(this.program, values);
		}
		return values;
	}

	private void forget(final int location) {
		final IntMap<int[]> values = this.uniforms.get(this.program);
		if (values != null) {
			values.remove(location);
		}
	}

	/** Stores the value; true if it differs from the stored one (or there was none). */
	private boolean changed(final int location, final int kind, final int[] bits, final int count) {
		if ((location < 0) || (this.program == 0)) {
			return true;
		}
		final IntMap<int[]> values = programUniforms();
		int[] old = values.get(location);
		if ((old != null) && (old.length == (count + 2)) && (old[0] == kind) && (old[1] == count)) {
			boolean same = true;
			for (int i = 0; i < count; i++) {
				if (old[i + 2] != bits[i]) {
					same = false;
					break;
				}
			}
			if (same) {
				return false;
			}
		}
		else {
			old = new int[count + 2];
			values.put(location, old);
		}
		old[0] = kind;
		old[1] = count;
		for (int i = 0; i < count; i++) {
			old[i + 2] = bits[i];
		}
		return true;
	}

	private int[] slot(final int location, final int kind, final int count) {
		final IntMap<int[]> values = programUniforms();
		int[] old = values.get(location);
		if ((old == null) || (old.length != (count + 2))) {
			old = new int[count + 2];
			old[0] = -1;
			values.put(location, old);
		}
		return old;
	}

	private boolean changedFloats(final int location, final int kind, final float[] v, final int offset, final int count) {
		if ((location < 0) || (this.program == 0) || (count <= 0) || ((offset + count) > v.length)) {
			if (location >= 0) {
				forget(location);
			}
			return true;
		}
		final int[] old = slot(location, kind, count);
		boolean same = (old[0] == kind) && (old[1] == count);
		for (int i = 0; i < count; i++) {
			final int bits = Float.floatToRawIntBits(v[offset + i]);
			if (old[i + 2] != bits) {
				same = false;
				old[i + 2] = bits;
			}
		}
		old[0] = kind;
		old[1] = count;
		return !same;
	}

	private boolean changedFloats(final int location, final int kind, final java.nio.FloatBuffer v, final int count) {
		final int position = v.position();
		if ((location < 0) || (this.program == 0) || (count <= 0) || ((position + count) > v.limit())) {
			if (location >= 0) {
				forget(location);
			}
			return true;
		}
		final int[] old = slot(location, kind, count);
		boolean same = (old[0] == kind) && (old[1] == count);
		for (int i = 0; i < count; i++) {
			final int bits = Float.floatToRawIntBits(v.get(position + i));
			if (old[i + 2] != bits) {
				same = false;
				old[i + 2] = bits;
			}
		}
		old[0] = kind;
		old[1] = count;
		return !same;
	}

	private boolean changedInts(final int location, final int kind, final int[] v, final int offset, final int count) {
		if ((location < 0) || (this.program == 0) || (count <= 0) || ((offset + count) > v.length)) {
			if (location >= 0) {
				forget(location);
			}
			return true;
		}
		final int[] old = slot(location, kind, count);
		boolean same = (old[0] == kind) && (old[1] == count);
		for (int i = 0; i < count; i++) {
			if (old[i + 2] != v[offset + i]) {
				same = false;
				old[i + 2] = v[offset + i];
			}
		}
		old[0] = kind;
		old[1] = count;
		return !same;
	}

	private boolean changedInts(final int location, final int kind, final java.nio.IntBuffer v, final int count) {
		final int position = v.position();
		if ((location < 0) || (this.program == 0) || (count <= 0) || ((position + count) > v.limit())) {
			if (location >= 0) {
				forget(location);
			}
			return true;
		}
		final int[] old = slot(location, kind, count);
		boolean same = (old[0] == kind) && (old[1] == count);
		for (int i = 0; i < count; i++) {
			final int value = v.get(position + i);
			if (old[i + 2] != value) {
				same = false;
				old[i + 2] = value;
			}
		}
		old[0] = kind;
		old[1] = count;
		return !same;
	}

	@Override
	public boolean glIsBuffer(final int a0) {
		return this.gl.glIsBuffer(a0);
	}

	@Override
	public boolean glIsEnabled(final int a0) {
		return this.gl.glIsEnabled(a0);
	}

	@Override
	public boolean glIsFramebuffer(final int a0) {
		return this.gl.glIsFramebuffer(a0);
	}

	@Override
	public boolean glIsProgram(final int a0) {
		return this.gl.glIsProgram(a0);
	}

	@Override
	public boolean glIsRenderbuffer(final int a0) {
		return this.gl.glIsRenderbuffer(a0);
	}

	@Override
	public boolean glIsShader(final int a0) {
		return this.gl.glIsShader(a0);
	}

	@Override
	public boolean glIsTexture(final int a0) {
		return this.gl.glIsTexture(a0);
	}

	@Override
	public int glCheckFramebufferStatus(final int a0) {
		return this.gl.glCheckFramebufferStatus(a0);
	}

	@Override
	public int glCreateProgram() {
		return this.gl.glCreateProgram();
	}

	@Override
	public int glCreateShader(final int a0) {
		return this.gl.glCreateShader(a0);
	}

	@Override
	public int glGenBuffer() {
		return this.gl.glGenBuffer();
	}

	@Override
	public int glGenFramebuffer() {
		return this.gl.glGenFramebuffer();
	}

	@Override
	public int glGenRenderbuffer() {
		return this.gl.glGenRenderbuffer();
	}

	@Override
	public int glGenTexture() {
		return this.gl.glGenTexture();
	}

	@Override
	public int glGetAttribLocation(final int a0, final java.lang.String a1) {
		return this.gl.glGetAttribLocation(a0, a1);
	}

	@Override
	public int glGetError() {
		return this.gl.glGetError();
	}

	@Override
	public int glGetUniformLocation(final int a0, final java.lang.String a1) {
		return this.gl.glGetUniformLocation(a0, a1);
	}

	@Override
	public java.lang.String glGetActiveAttrib(final int a0, final int a1, final java.nio.IntBuffer a2, final java.nio.IntBuffer a3) {
		return this.gl.glGetActiveAttrib(a0, a1, a2, a3);
	}

	@Override
	public java.lang.String glGetActiveUniform(final int a0, final int a1, final java.nio.IntBuffer a2, final java.nio.IntBuffer a3) {
		return this.gl.glGetActiveUniform(a0, a1, a2, a3);
	}

	@Override
	public java.lang.String glGetProgramInfoLog(final int a0) {
		return this.gl.glGetProgramInfoLog(a0);
	}

	@Override
	public java.lang.String glGetShaderInfoLog(final int a0) {
		return this.gl.glGetShaderInfoLog(a0);
	}

	@Override
	public java.lang.String glGetString(final int a0) {
		return this.gl.glGetString(a0);
	}

	@Override
	public void glActiveTexture(final int a0) {
		this.gl.glActiveTexture(a0);
	}

	@Override
	public void glAttachShader(final int a0, final int a1) {
		this.gl.glAttachShader(a0, a1);
	}

	@Override
	public void glBindAttribLocation(final int a0, final int a1, final java.lang.String a2) {
		this.gl.glBindAttribLocation(a0, a1, a2);
	}

	@Override
	public void glBindBuffer(final int a0, final int a1) {
		this.gl.glBindBuffer(a0, a1);
	}

	@Override
	public void glBindFramebuffer(final int a0, final int a1) {
		this.gl.glBindFramebuffer(a0, a1);
	}

	@Override
	public void glBindRenderbuffer(final int a0, final int a1) {
		this.gl.glBindRenderbuffer(a0, a1);
	}

	@Override
	public void glBindTexture(final int a0, final int a1) {
		this.gl.glBindTexture(a0, a1);
	}

	@Override
	public void glBlendColor(final float a0, final float a1, final float a2, final float a3) {
		this.gl.glBlendColor(a0, a1, a2, a3);
	}

	@Override
	public void glBlendEquation(final int a0) {
		this.gl.glBlendEquation(a0);
	}

	@Override
	public void glBlendEquationSeparate(final int a0, final int a1) {
		this.gl.glBlendEquationSeparate(a0, a1);
	}

	@Override
	public void glBlendFunc(final int a0, final int a1) {
		if (setBlend(a0, a1, a0, a1)) {
			this.gl.glBlendFunc(a0, a1);
		}
	}

	@Override
	public void glBlendFuncSeparate(final int a0, final int a1, final int a2, final int a3) {
		if (setBlend(a0, a1, a2, a3)) {
			this.gl.glBlendFuncSeparate(a0, a1, a2, a3);
		}
	}

	@Override
	public void glBufferData(final int a0, final int a1, final java.nio.Buffer a2, final int a3) {
		this.gl.glBufferData(a0, a1, a2, a3);
	}

	@Override
	public void glBufferSubData(final int a0, final int a1, final int a2, final java.nio.Buffer a3) {
		this.gl.glBufferSubData(a0, a1, a2, a3);
	}

	@Override
	public void glClear(final int a0) {
		this.gl.glClear(a0);
	}

	@Override
	public void glClearColor(final float a0, final float a1, final float a2, final float a3) {
		this.gl.glClearColor(a0, a1, a2, a3);
	}

	@Override
	public void glClearDepthf(final float a0) {
		this.gl.glClearDepthf(a0);
	}

	@Override
	public void glClearStencil(final int a0) {
		this.gl.glClearStencil(a0);
	}

	@Override
	public void glColorMask(final boolean a0, final boolean a1, final boolean a2, final boolean a3) {
		this.gl.glColorMask(a0, a1, a2, a3);
	}

	@Override
	public void glCompileShader(final int a0) {
		this.gl.glCompileShader(a0);
	}

	@Override
	public void glCompressedTexImage2D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final java.nio.Buffer a7) {
		this.gl.glCompressedTexImage2D(a0, a1, a2, a3, a4, a5, a6, a7);
	}

	@Override
	public void glCompressedTexSubImage2D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final java.nio.Buffer a8) {
		this.gl.glCompressedTexSubImage2D(a0, a1, a2, a3, a4, a5, a6, a7, a8);
	}

	@Override
	public void glCopyTexImage2D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7) {
		this.gl.glCopyTexImage2D(a0, a1, a2, a3, a4, a5, a6, a7);
	}

	@Override
	public void glCopyTexSubImage2D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7) {
		this.gl.glCopyTexSubImage2D(a0, a1, a2, a3, a4, a5, a6, a7);
	}

	@Override
	public void glCullFace(final int a0) {
		this.gl.glCullFace(a0);
	}

	@Override
	public void glDeleteBuffer(final int a0) {
		this.gl.glDeleteBuffer(a0);
	}

	@Override
	public void glDeleteBuffers(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glDeleteBuffers(a0, a1);
	}

	@Override
	public void glDeleteFramebuffer(final int a0) {
		this.gl.glDeleteFramebuffer(a0);
	}

	@Override
	public void glDeleteFramebuffers(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glDeleteFramebuffers(a0, a1);
	}

	@Override
	public void glDeleteProgram(final int a0) {
		this.uniforms.remove(a0);
		this.gl.glDeleteProgram(a0);
	}

	@Override
	public void glDeleteRenderbuffer(final int a0) {
		this.gl.glDeleteRenderbuffer(a0);
	}

	@Override
	public void glDeleteRenderbuffers(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glDeleteRenderbuffers(a0, a1);
	}

	@Override
	public void glDeleteShader(final int a0) {
		this.gl.glDeleteShader(a0);
	}

	@Override
	public void glDeleteTexture(final int a0) {
		this.gl.glDeleteTexture(a0);
	}

	@Override
	public void glDeleteTextures(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glDeleteTextures(a0, a1);
	}

	@Override
	public void glDepthFunc(final int a0) {
		this.gl.glDepthFunc(a0);
	}

	@Override
	public void glDepthMask(final boolean a0) {
		final int value = a0 ? 1 : 0;
		if (this.depthMask != value) {
			this.depthMask = value;
			this.gl.glDepthMask(a0);
		}
	}

	@Override
	public void glDepthRangef(final float a0, final float a1) {
		this.gl.glDepthRangef(a0, a1);
	}

	@Override
	public void glDetachShader(final int a0, final int a1) {
		this.gl.glDetachShader(a0, a1);
	}

	@Override
	public void glDisable(final int a0) {
		if (setCap(a0, 0)) {
			this.gl.glDisable(a0);
		}
	}

	@Override
	public void glDisableVertexAttribArray(final int a0) {
		this.gl.glDisableVertexAttribArray(a0);
	}

	@Override
	public void glDrawArrays(final int a0, final int a1, final int a2) {
		this.gl.glDrawArrays(a0, a1, a2);
	}

	@Override
	public void glDrawElements(final int a0, final int a1, final int a2, final int a3) {
		this.gl.glDrawElements(a0, a1, a2, a3);
	}

	@Override
	public void glDrawElements(final int a0, final int a1, final int a2, final java.nio.Buffer a3) {
		this.gl.glDrawElements(a0, a1, a2, a3);
	}

	@Override
	public void glEnable(final int a0) {
		if (setCap(a0, 1)) {
			this.gl.glEnable(a0);
		}
	}

	@Override
	public void glEnableVertexAttribArray(final int a0) {
		this.gl.glEnableVertexAttribArray(a0);
	}

	@Override
	public void glFinish() {
		this.gl.glFinish();
	}

	@Override
	public void glFlush() {
		this.gl.glFlush();
	}

	@Override
	public void glFramebufferRenderbuffer(final int a0, final int a1, final int a2, final int a3) {
		this.gl.glFramebufferRenderbuffer(a0, a1, a2, a3);
	}

	@Override
	public void glFramebufferTexture2D(final int a0, final int a1, final int a2, final int a3, final int a4) {
		this.gl.glFramebufferTexture2D(a0, a1, a2, a3, a4);
	}

	@Override
	public void glFrontFace(final int a0) {
		this.gl.glFrontFace(a0);
	}

	@Override
	public void glGenBuffers(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glGenBuffers(a0, a1);
	}

	@Override
	public void glGenFramebuffers(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glGenFramebuffers(a0, a1);
	}

	@Override
	public void glGenRenderbuffers(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glGenRenderbuffers(a0, a1);
	}

	@Override
	public void glGenTextures(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glGenTextures(a0, a1);
	}

	@Override
	public void glGenerateMipmap(final int a0) {
		this.gl.glGenerateMipmap(a0);
	}

	@Override
	public void glGetAttachedShaders(final int a0, final int a1, final java.nio.Buffer a2, final java.nio.IntBuffer a3) {
		this.gl.glGetAttachedShaders(a0, a1, a2, a3);
	}

	@Override
	public void glGetBooleanv(final int a0, final java.nio.Buffer a1) {
		this.gl.glGetBooleanv(a0, a1);
	}

	@Override
	public void glGetBufferParameteriv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetBufferParameteriv(a0, a1, a2);
	}

	@Override
	public void glGetFloatv(final int a0, final java.nio.FloatBuffer a1) {
		this.gl.glGetFloatv(a0, a1);
	}

	@Override
	public void glGetFramebufferAttachmentParameteriv(final int a0, final int a1, final int a2, final java.nio.IntBuffer a3) {
		this.gl.glGetFramebufferAttachmentParameteriv(a0, a1, a2, a3);
	}

	@Override
	public void glGetIntegerv(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glGetIntegerv(a0, a1);
	}

	@Override
	public void glGetProgramiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetProgramiv(a0, a1, a2);
	}

	@Override
	public void glGetRenderbufferParameteriv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetRenderbufferParameteriv(a0, a1, a2);
	}

	@Override
	public void glGetShaderPrecisionFormat(final int a0, final int a1, final java.nio.IntBuffer a2, final java.nio.IntBuffer a3) {
		this.gl.glGetShaderPrecisionFormat(a0, a1, a2, a3);
	}

	@Override
	public void glGetShaderiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetShaderiv(a0, a1, a2);
	}

	@Override
	public void glGetTexParameterfv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		this.gl.glGetTexParameterfv(a0, a1, a2);
	}

	@Override
	public void glGetTexParameteriv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetTexParameteriv(a0, a1, a2);
	}

	@Override
	public void glGetUniformfv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		this.gl.glGetUniformfv(a0, a1, a2);
	}

	@Override
	public void glGetUniformiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetUniformiv(a0, a1, a2);
	}

	@Override
	public void glGetVertexAttribPointerv(final int a0, final int a1, final java.nio.Buffer a2) {
		this.gl.glGetVertexAttribPointerv(a0, a1, a2);
	}

	@Override
	public void glGetVertexAttribfv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		this.gl.glGetVertexAttribfv(a0, a1, a2);
	}

	@Override
	public void glGetVertexAttribiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetVertexAttribiv(a0, a1, a2);
	}

	@Override
	public void glHint(final int a0, final int a1) {
		this.gl.glHint(a0, a1);
	}

	@Override
	public void glLineWidth(final float a0) {
		this.gl.glLineWidth(a0);
	}

	@Override
	public void glLinkProgram(final int a0) {
		this.uniforms.remove(a0);
		this.gl.glLinkProgram(a0);
	}

	@Override
	public void glPixelStorei(final int a0, final int a1) {
		this.gl.glPixelStorei(a0, a1);
	}

	@Override
	public void glPolygonOffset(final float a0, final float a1) {
		this.gl.glPolygonOffset(a0, a1);
	}

	@Override
	public void glReadPixels(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final java.nio.Buffer a6) {
		this.gl.glReadPixels(a0, a1, a2, a3, a4, a5, a6);
	}

	@Override
	public void glReleaseShaderCompiler() {
		this.gl.glReleaseShaderCompiler();
	}

	@Override
	public void glRenderbufferStorage(final int a0, final int a1, final int a2, final int a3) {
		this.gl.glRenderbufferStorage(a0, a1, a2, a3);
	}

	@Override
	public void glSampleCoverage(final float a0, final boolean a1) {
		this.gl.glSampleCoverage(a0, a1);
	}

	@Override
	public void glScissor(final int a0, final int a1, final int a2, final int a3) {
		this.gl.glScissor(a0, a1, a2, a3);
	}

	@Override
	public void glShaderBinary(final int a0, final java.nio.IntBuffer a1, final int a2, final java.nio.Buffer a3, final int a4) {
		this.gl.glShaderBinary(a0, a1, a2, a3, a4);
	}

	@Override
	public void glShaderSource(final int a0, final java.lang.String a1) {
		this.gl.glShaderSource(a0, a1);
	}

	@Override
	public void glStencilFunc(final int a0, final int a1, final int a2) {
		this.gl.glStencilFunc(a0, a1, a2);
	}

	@Override
	public void glStencilFuncSeparate(final int a0, final int a1, final int a2, final int a3) {
		this.gl.glStencilFuncSeparate(a0, a1, a2, a3);
	}

	@Override
	public void glStencilMask(final int a0) {
		this.gl.glStencilMask(a0);
	}

	@Override
	public void glStencilMaskSeparate(final int a0, final int a1) {
		this.gl.glStencilMaskSeparate(a0, a1);
	}

	@Override
	public void glStencilOp(final int a0, final int a1, final int a2) {
		this.gl.glStencilOp(a0, a1, a2);
	}

	@Override
	public void glStencilOpSeparate(final int a0, final int a1, final int a2, final int a3) {
		this.gl.glStencilOpSeparate(a0, a1, a2, a3);
	}

	@Override
	public void glTexImage2D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final java.nio.Buffer a8) {
		this.gl.glTexImage2D(a0, a1, a2, a3, a4, a5, a6, a7, a8);
	}

	@Override
	public void glTexParameterf(final int a0, final int a1, final float a2) {
		this.gl.glTexParameterf(a0, a1, a2);
	}

	@Override
	public void glTexParameterfv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		this.gl.glTexParameterfv(a0, a1, a2);
	}

	@Override
	public void glTexParameteri(final int a0, final int a1, final int a2) {
		this.gl.glTexParameteri(a0, a1, a2);
	}

	@Override
	public void glTexParameteriv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glTexParameteriv(a0, a1, a2);
	}

	@Override
	public void glTexSubImage2D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final java.nio.Buffer a8) {
		this.gl.glTexSubImage2D(a0, a1, a2, a3, a4, a5, a6, a7, a8);
	}

	@Override
	public void glUniform1f(final int a0, final float a1) {
		final int[] v = this.scratch;
		v[0] = Float.floatToRawIntBits(a1);
		if (changed(a0, 1, v, 1)) {
			this.gl.glUniform1f(a0, a1);
		}
	}

	@Override
	public void glUniform1fv(final int a0, final int a1, final float[] a2, final int a3) {
		if (changedFloats(a0, 1, a2, a3, a1 * 1)) {
			this.gl.glUniform1fv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniform1fv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		if (changedFloats(a0, 1, a2, a1 * 1)) {
			this.gl.glUniform1fv(a0, a1, a2);
		}
	}

	@Override
	public void glUniform1i(final int a0, final int a1) {
		final int[] v = this.scratch;
		v[0] = a1;
		if (changed(a0, 2, v, 1)) {
			this.gl.glUniform1i(a0, a1);
		}
	}

	@Override
	public void glUniform1iv(final int a0, final int a1, final int[] a2, final int a3) {
		if (changedInts(a0, 2, a2, a3, a1 * 1)) {
			this.gl.glUniform1iv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniform1iv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		if (changedInts(a0, 2, a2, a1 * 1)) {
			this.gl.glUniform1iv(a0, a1, a2);
		}
	}

	@Override
	public void glUniform2f(final int a0, final float a1, final float a2) {
		final int[] v = this.scratch;
		v[0] = Float.floatToRawIntBits(a1);
		v[1] = Float.floatToRawIntBits(a2);
		if (changed(a0, 3, v, 2)) {
			this.gl.glUniform2f(a0, a1, a2);
		}
	}

	@Override
	public void glUniform2fv(final int a0, final int a1, final float[] a2, final int a3) {
		if (changedFloats(a0, 3, a2, a3, a1 * 2)) {
			this.gl.glUniform2fv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniform2fv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		if (changedFloats(a0, 3, a2, a1 * 2)) {
			this.gl.glUniform2fv(a0, a1, a2);
		}
	}

	@Override
	public void glUniform2i(final int a0, final int a1, final int a2) {
		final int[] v = this.scratch;
		v[0] = a1;
		v[1] = a2;
		if (changed(a0, 4, v, 2)) {
			this.gl.glUniform2i(a0, a1, a2);
		}
	}

	@Override
	public void glUniform2iv(final int a0, final int a1, final int[] a2, final int a3) {
		if (changedInts(a0, 4, a2, a3, a1 * 2)) {
			this.gl.glUniform2iv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniform2iv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		if (changedInts(a0, 4, a2, a1 * 2)) {
			this.gl.glUniform2iv(a0, a1, a2);
		}
	}

	@Override
	public void glUniform3f(final int a0, final float a1, final float a2, final float a3) {
		final int[] v = this.scratch;
		v[0] = Float.floatToRawIntBits(a1);
		v[1] = Float.floatToRawIntBits(a2);
		v[2] = Float.floatToRawIntBits(a3);
		if (changed(a0, 5, v, 3)) {
			this.gl.glUniform3f(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniform3fv(final int a0, final int a1, final float[] a2, final int a3) {
		if (changedFloats(a0, 5, a2, a3, a1 * 3)) {
			this.gl.glUniform3fv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniform3fv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		if (changedFloats(a0, 5, a2, a1 * 3)) {
			this.gl.glUniform3fv(a0, a1, a2);
		}
	}

	@Override
	public void glUniform3i(final int a0, final int a1, final int a2, final int a3) {
		final int[] v = this.scratch;
		v[0] = a1;
		v[1] = a2;
		v[2] = a3;
		if (changed(a0, 6, v, 3)) {
			this.gl.glUniform3i(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniform3iv(final int a0, final int a1, final int[] a2, final int a3) {
		if (changedInts(a0, 6, a2, a3, a1 * 3)) {
			this.gl.glUniform3iv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniform3iv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		if (changedInts(a0, 6, a2, a1 * 3)) {
			this.gl.glUniform3iv(a0, a1, a2);
		}
	}

	@Override
	public void glUniform4f(final int a0, final float a1, final float a2, final float a3, final float a4) {
		final int[] v = this.scratch;
		v[0] = Float.floatToRawIntBits(a1);
		v[1] = Float.floatToRawIntBits(a2);
		v[2] = Float.floatToRawIntBits(a3);
		v[3] = Float.floatToRawIntBits(a4);
		if (changed(a0, 7, v, 4)) {
			this.gl.glUniform4f(a0, a1, a2, a3, a4);
		}
	}

	@Override
	public void glUniform4fv(final int a0, final int a1, final float[] a2, final int a3) {
		if (changedFloats(a0, 7, a2, a3, a1 * 4)) {
			this.gl.glUniform4fv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniform4fv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		if (changedFloats(a0, 7, a2, a1 * 4)) {
			this.gl.glUniform4fv(a0, a1, a2);
		}
	}

	@Override
	public void glUniform4i(final int a0, final int a1, final int a2, final int a3, final int a4) {
		final int[] v = this.scratch;
		v[0] = a1;
		v[1] = a2;
		v[2] = a3;
		v[3] = a4;
		if (changed(a0, 8, v, 4)) {
			this.gl.glUniform4i(a0, a1, a2, a3, a4);
		}
	}

	@Override
	public void glUniform4iv(final int a0, final int a1, final int[] a2, final int a3) {
		if (changedInts(a0, 8, a2, a3, a1 * 4)) {
			this.gl.glUniform4iv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniform4iv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		if (changedInts(a0, 8, a2, a1 * 4)) {
			this.gl.glUniform4iv(a0, a1, a2);
		}
	}

	@Override
	public void glUniformMatrix2fv(final int a0, final int a1, final boolean a2, final float[] a3, final int a4) {
		if (a2 || changedFloats(a0, 9, a3, a4, a1 * 4)) {
			if (a2) {
				forget(a0);
			}
			this.gl.glUniformMatrix2fv(a0, a1, a2, a3, a4);
		}
	}

	@Override
	public void glUniformMatrix2fv(final int a0, final int a1, final boolean a2, final java.nio.FloatBuffer a3) {
		if (a2 || changedFloats(a0, 9, a3, a1 * 4)) {
			if (a2) {
				forget(a0);
			}
			this.gl.glUniformMatrix2fv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniformMatrix3fv(final int a0, final int a1, final boolean a2, final float[] a3, final int a4) {
		if (a2 || changedFloats(a0, 10, a3, a4, a1 * 9)) {
			if (a2) {
				forget(a0);
			}
			this.gl.glUniformMatrix3fv(a0, a1, a2, a3, a4);
		}
	}

	@Override
	public void glUniformMatrix3fv(final int a0, final int a1, final boolean a2, final java.nio.FloatBuffer a3) {
		if (a2 || changedFloats(a0, 10, a3, a1 * 9)) {
			if (a2) {
				forget(a0);
			}
			this.gl.glUniformMatrix3fv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUniformMatrix4fv(final int a0, final int a1, final boolean a2, final float[] a3, final int a4) {
		if (a2 || changedFloats(a0, 11, a3, a4, a1 * 16)) {
			if (a2) {
				forget(a0);
			}
			this.gl.glUniformMatrix4fv(a0, a1, a2, a3, a4);
		}
	}

	@Override
	public void glUniformMatrix4fv(final int a0, final int a1, final boolean a2, final java.nio.FloatBuffer a3) {
		if (a2 || changedFloats(a0, 11, a3, a1 * 16)) {
			if (a2) {
				forget(a0);
			}
			this.gl.glUniformMatrix4fv(a0, a1, a2, a3);
		}
	}

	@Override
	public void glUseProgram(final int a0) {
		this.program = a0;
		this.gl.glUseProgram(a0);
	}

	@Override
	public void glValidateProgram(final int a0) {
		this.gl.glValidateProgram(a0);
	}

	@Override
	public void glVertexAttrib1f(final int a0, final float a1) {
		this.gl.glVertexAttrib1f(a0, a1);
	}

	@Override
	public void glVertexAttrib1fv(final int a0, final java.nio.FloatBuffer a1) {
		this.gl.glVertexAttrib1fv(a0, a1);
	}

	@Override
	public void glVertexAttrib2f(final int a0, final float a1, final float a2) {
		this.gl.glVertexAttrib2f(a0, a1, a2);
	}

	@Override
	public void glVertexAttrib2fv(final int a0, final java.nio.FloatBuffer a1) {
		this.gl.glVertexAttrib2fv(a0, a1);
	}

	@Override
	public void glVertexAttrib3f(final int a0, final float a1, final float a2, final float a3) {
		this.gl.glVertexAttrib3f(a0, a1, a2, a3);
	}

	@Override
	public void glVertexAttrib3fv(final int a0, final java.nio.FloatBuffer a1) {
		this.gl.glVertexAttrib3fv(a0, a1);
	}

	@Override
	public void glVertexAttrib4f(final int a0, final float a1, final float a2, final float a3, final float a4) {
		this.gl.glVertexAttrib4f(a0, a1, a2, a3, a4);
	}

	@Override
	public void glVertexAttrib4fv(final int a0, final java.nio.FloatBuffer a1) {
		this.gl.glVertexAttrib4fv(a0, a1);
	}

	@Override
	public void glVertexAttribPointer(final int a0, final int a1, final int a2, final boolean a3, final int a4, final int a5) {
		this.gl.glVertexAttribPointer(a0, a1, a2, a3, a4, a5);
	}

	@Override
	public void glVertexAttribPointer(final int a0, final int a1, final int a2, final boolean a3, final int a4, final java.nio.Buffer a5) {
		this.gl.glVertexAttribPointer(a0, a1, a2, a3, a4, a5);
	}

	@Override
	public void glViewport(final int a0, final int a1, final int a2, final int a3) {
		this.gl.glViewport(a0, a1, a2, a3);
	}

	@Override
	public boolean glIsQuery(final int a0) {
		return this.gl.glIsQuery(a0);
	}

	@Override
	public boolean glIsSampler(final int a0) {
		return this.gl.glIsSampler(a0);
	}

	@Override
	public boolean glIsTransformFeedback(final int a0) {
		return this.gl.glIsTransformFeedback(a0);
	}

	@Override
	public boolean glIsVertexArray(final int a0) {
		return this.gl.glIsVertexArray(a0);
	}

	@Override
	public boolean glUnmapBuffer(final int a0) {
		return this.gl.glUnmapBuffer(a0);
	}

	@Override
	public int glGetFragDataLocation(final int a0, final java.lang.String a1) {
		return this.gl.glGetFragDataLocation(a0, a1);
	}

	@Override
	public int glGetUniformBlockIndex(final int a0, final java.lang.String a1) {
		return this.gl.glGetUniformBlockIndex(a0, a1);
	}

	@Override
	public java.lang.String glGetActiveUniformBlockName(final int a0, final int a1) {
		return this.gl.glGetActiveUniformBlockName(a0, a1);
	}

	@Override
	public java.lang.String glGetStringi(final int a0, final int a1) {
		return this.gl.glGetStringi(a0, a1);
	}

	@Override
	public java.nio.Buffer glGetBufferPointerv(final int a0, final int a1) {
		return this.gl.glGetBufferPointerv(a0, a1);
	}

	@Override
	public java.nio.Buffer glMapBufferRange(final int a0, final int a1, final int a2, final int a3) {
		return this.gl.glMapBufferRange(a0, a1, a2, a3);
	}

	@Override
	public void glBeginQuery(final int a0, final int a1) {
		this.gl.glBeginQuery(a0, a1);
	}

	@Override
	public void glBeginTransformFeedback(final int a0) {
		this.gl.glBeginTransformFeedback(a0);
	}

	@Override
	public void glBindBufferBase(final int a0, final int a1, final int a2) {
		this.gl.glBindBufferBase(a0, a1, a2);
	}

	@Override
	public void glBindBufferRange(final int a0, final int a1, final int a2, final int a3, final int a4) {
		this.gl.glBindBufferRange(a0, a1, a2, a3, a4);
	}

	@Override
	public void glBindSampler(final int a0, final int a1) {
		this.gl.glBindSampler(a0, a1);
	}

	@Override
	public void glBindTransformFeedback(final int a0, final int a1) {
		this.gl.glBindTransformFeedback(a0, a1);
	}

	@Override
	public void glBindVertexArray(final int a0) {
		this.gl.glBindVertexArray(a0);
	}

	@Override
	public void glBlitFramebuffer(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final int a8, final int a9) {
		this.gl.glBlitFramebuffer(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9);
	}

	@Override
	public void glClearBufferfi(final int a0, final int a1, final float a2, final int a3) {
		this.gl.glClearBufferfi(a0, a1, a2, a3);
	}

	@Override
	public void glClearBufferfv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		this.gl.glClearBufferfv(a0, a1, a2);
	}

	@Override
	public void glClearBufferiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glClearBufferiv(a0, a1, a2);
	}

	@Override
	public void glClearBufferuiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glClearBufferuiv(a0, a1, a2);
	}

	@Override
	public void glCopyBufferSubData(final int a0, final int a1, final int a2, final int a3, final int a4) {
		this.gl.glCopyBufferSubData(a0, a1, a2, a3, a4);
	}

	@Override
	public void glCopyTexSubImage3D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final int a8) {
		this.gl.glCopyTexSubImage3D(a0, a1, a2, a3, a4, a5, a6, a7, a8);
	}

	@Override
	public void glDeleteQueries(final int a0, final int[] a1, final int a2) {
		this.gl.glDeleteQueries(a0, a1, a2);
	}

	@Override
	public void glDeleteQueries(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glDeleteQueries(a0, a1);
	}

	@Override
	public void glDeleteSamplers(final int a0, final int[] a1, final int a2) {
		this.gl.glDeleteSamplers(a0, a1, a2);
	}

	@Override
	public void glDeleteSamplers(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glDeleteSamplers(a0, a1);
	}

	@Override
	public void glDeleteTransformFeedbacks(final int a0, final int[] a1, final int a2) {
		this.gl.glDeleteTransformFeedbacks(a0, a1, a2);
	}

	@Override
	public void glDeleteTransformFeedbacks(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glDeleteTransformFeedbacks(a0, a1);
	}

	@Override
	public void glDeleteVertexArrays(final int a0, final int[] a1, final int a2) {
		this.gl.glDeleteVertexArrays(a0, a1, a2);
	}

	@Override
	public void glDeleteVertexArrays(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glDeleteVertexArrays(a0, a1);
	}

	@Override
	public void glDrawArraysInstanced(final int a0, final int a1, final int a2, final int a3) {
		this.gl.glDrawArraysInstanced(a0, a1, a2, a3);
	}

	@Override
	public void glDrawBuffers(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glDrawBuffers(a0, a1);
	}

	@Override
	public void glDrawElementsInstanced(final int a0, final int a1, final int a2, final int a3, final int a4) {
		this.gl.glDrawElementsInstanced(a0, a1, a2, a3, a4);
	}

	@Override
	public void glDrawRangeElements(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5) {
		this.gl.glDrawRangeElements(a0, a1, a2, a3, a4, a5);
	}

	@Override
	public void glDrawRangeElements(final int a0, final int a1, final int a2, final int a3, final int a4, final java.nio.Buffer a5) {
		this.gl.glDrawRangeElements(a0, a1, a2, a3, a4, a5);
	}

	@Override
	public void glEndQuery(final int a0) {
		this.gl.glEndQuery(a0);
	}

	@Override
	public void glEndTransformFeedback() {
		this.gl.glEndTransformFeedback();
	}

	@Override
	public void glFlushMappedBufferRange(final int a0, final int a1, final int a2) {
		this.gl.glFlushMappedBufferRange(a0, a1, a2);
	}

	@Override
	public void glFramebufferTextureLayer(final int a0, final int a1, final int a2, final int a3, final int a4) {
		this.gl.glFramebufferTextureLayer(a0, a1, a2, a3, a4);
	}

	@Override
	public void glGenQueries(final int a0, final int[] a1, final int a2) {
		this.gl.glGenQueries(a0, a1, a2);
	}

	@Override
	public void glGenQueries(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glGenQueries(a0, a1);
	}

	@Override
	public void glGenSamplers(final int a0, final int[] a1, final int a2) {
		this.gl.glGenSamplers(a0, a1, a2);
	}

	@Override
	public void glGenSamplers(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glGenSamplers(a0, a1);
	}

	@Override
	public void glGenTransformFeedbacks(final int a0, final int[] a1, final int a2) {
		this.gl.glGenTransformFeedbacks(a0, a1, a2);
	}

	@Override
	public void glGenTransformFeedbacks(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glGenTransformFeedbacks(a0, a1);
	}

	@Override
	public void glGenVertexArrays(final int a0, final int[] a1, final int a2) {
		this.gl.glGenVertexArrays(a0, a1, a2);
	}

	@Override
	public void glGenVertexArrays(final int a0, final java.nio.IntBuffer a1) {
		this.gl.glGenVertexArrays(a0, a1);
	}

	@Override
	public void glGetActiveUniformBlockName(final int a0, final int a1, final java.nio.Buffer a2, final java.nio.Buffer a3) {
		this.gl.glGetActiveUniformBlockName(a0, a1, a2, a3);
	}

	@Override
	public void glGetActiveUniformBlockiv(final int a0, final int a1, final int a2, final java.nio.IntBuffer a3) {
		this.gl.glGetActiveUniformBlockiv(a0, a1, a2, a3);
	}

	@Override
	public void glGetActiveUniformsiv(final int a0, final int a1, final java.nio.IntBuffer a2, final int a3, final java.nio.IntBuffer a4) {
		this.gl.glGetActiveUniformsiv(a0, a1, a2, a3, a4);
	}

	@Override
	public void glGetBufferParameteri64v(final int a0, final int a1, final java.nio.LongBuffer a2) {
		this.gl.glGetBufferParameteri64v(a0, a1, a2);
	}

	@Override
	public void glGetInteger64v(final int a0, final java.nio.LongBuffer a1) {
		this.gl.glGetInteger64v(a0, a1);
	}

	@Override
	public void glGetQueryObjectuiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetQueryObjectuiv(a0, a1, a2);
	}

	@Override
	public void glGetQueryiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetQueryiv(a0, a1, a2);
	}

	@Override
	public void glGetSamplerParameterfv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		this.gl.glGetSamplerParameterfv(a0, a1, a2);
	}

	@Override
	public void glGetSamplerParameteriv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetSamplerParameteriv(a0, a1, a2);
	}

	@Override
	public void glGetUniformIndices(final int a0, final java.lang.String[] a1, final java.nio.IntBuffer a2) {
		this.gl.glGetUniformIndices(a0, a1, a2);
	}

	@Override
	public void glGetUniformuiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetUniformuiv(a0, a1, a2);
	}

	@Override
	public void glGetVertexAttribIiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetVertexAttribIiv(a0, a1, a2);
	}

	@Override
	public void glGetVertexAttribIuiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glGetVertexAttribIuiv(a0, a1, a2);
	}

	@Override
	public void glInvalidateFramebuffer(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glInvalidateFramebuffer(a0, a1, a2);
	}

	@Override
	public void glInvalidateSubFramebuffer(final int a0, final int a1, final java.nio.IntBuffer a2, final int a3, final int a4, final int a5, final int a6) {
		this.gl.glInvalidateSubFramebuffer(a0, a1, a2, a3, a4, a5, a6);
	}

	@Override
	public void glPauseTransformFeedback() {
		this.gl.glPauseTransformFeedback();
	}

	@Override
	public void glProgramParameteri(final int a0, final int a1, final int a2) {
		this.gl.glProgramParameteri(a0, a1, a2);
	}

	@Override
	public void glReadBuffer(final int a0) {
		this.gl.glReadBuffer(a0);
	}

	@Override
	public void glRenderbufferStorageMultisample(final int a0, final int a1, final int a2, final int a3, final int a4) {
		this.gl.glRenderbufferStorageMultisample(a0, a1, a2, a3, a4);
	}

	@Override
	public void glResumeTransformFeedback() {
		this.gl.glResumeTransformFeedback();
	}

	@Override
	public void glSamplerParameterf(final int a0, final int a1, final float a2) {
		this.gl.glSamplerParameterf(a0, a1, a2);
	}

	@Override
	public void glSamplerParameterfv(final int a0, final int a1, final java.nio.FloatBuffer a2) {
		this.gl.glSamplerParameterfv(a0, a1, a2);
	}

	@Override
	public void glSamplerParameteri(final int a0, final int a1, final int a2) {
		this.gl.glSamplerParameteri(a0, a1, a2);
	}

	@Override
	public void glSamplerParameteriv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		this.gl.glSamplerParameteriv(a0, a1, a2);
	}

	@Override
	public void glTexImage2D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final int a8) {
		this.gl.glTexImage2D(a0, a1, a2, a3, a4, a5, a6, a7, a8);
	}

	@Override
	public void glTexImage3D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final int a8, final int a9) {
		this.gl.glTexImage3D(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9);
	}

	@Override
	public void glTexImage3D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final int a8, final java.nio.Buffer a9) {
		this.gl.glTexImage3D(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9);
	}

	@Override
	public void glTexSubImage2D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final int a8) {
		this.gl.glTexSubImage2D(a0, a1, a2, a3, a4, a5, a6, a7, a8);
	}

	@Override
	public void glTexSubImage3D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final int a8, final int a9, final int a10) {
		this.gl.glTexSubImage3D(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10);
	}

	@Override
	public void glTexSubImage3D(final int a0, final int a1, final int a2, final int a3, final int a4, final int a5, final int a6, final int a7, final int a8, final int a9, final java.nio.Buffer a10) {
		this.gl.glTexSubImage3D(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10);
	}

	@Override
	public void glTransformFeedbackVaryings(final int a0, final java.lang.String[] a1, final int a2) {
		this.gl.glTransformFeedbackVaryings(a0, a1, a2);
	}

	@Override
	public void glUniform1uiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		forget(a0);
		this.gl.glUniform1uiv(a0, a1, a2);
	}

	@Override
	public void glUniform3uiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		forget(a0);
		this.gl.glUniform3uiv(a0, a1, a2);
	}

	@Override
	public void glUniform4uiv(final int a0, final int a1, final java.nio.IntBuffer a2) {
		forget(a0);
		this.gl.glUniform4uiv(a0, a1, a2);
	}

	@Override
	public void glUniformBlockBinding(final int a0, final int a1, final int a2) {
		this.gl.glUniformBlockBinding(a0, a1, a2);
	}

	@Override
	public void glUniformMatrix2x3fv(final int a0, final int a1, final boolean a2, final java.nio.FloatBuffer a3) {
		forget(a0);
		this.gl.glUniformMatrix2x3fv(a0, a1, a2, a3);
	}

	@Override
	public void glUniformMatrix2x4fv(final int a0, final int a1, final boolean a2, final java.nio.FloatBuffer a3) {
		forget(a0);
		this.gl.glUniformMatrix2x4fv(a0, a1, a2, a3);
	}

	@Override
	public void glUniformMatrix3x2fv(final int a0, final int a1, final boolean a2, final java.nio.FloatBuffer a3) {
		forget(a0);
		this.gl.glUniformMatrix3x2fv(a0, a1, a2, a3);
	}

	@Override
	public void glUniformMatrix3x4fv(final int a0, final int a1, final boolean a2, final java.nio.FloatBuffer a3) {
		forget(a0);
		this.gl.glUniformMatrix3x4fv(a0, a1, a2, a3);
	}

	@Override
	public void glUniformMatrix4x2fv(final int a0, final int a1, final boolean a2, final java.nio.FloatBuffer a3) {
		forget(a0);
		this.gl.glUniformMatrix4x2fv(a0, a1, a2, a3);
	}

	@Override
	public void glUniformMatrix4x3fv(final int a0, final int a1, final boolean a2, final java.nio.FloatBuffer a3) {
		forget(a0);
		this.gl.glUniformMatrix4x3fv(a0, a1, a2, a3);
	}

	@Override
	public void glVertexAttribDivisor(final int a0, final int a1) {
		this.gl.glVertexAttribDivisor(a0, a1);
	}

	@Override
	public void glVertexAttribI4i(final int a0, final int a1, final int a2, final int a3, final int a4) {
		this.gl.glVertexAttribI4i(a0, a1, a2, a3, a4);
	}

	@Override
	public void glVertexAttribI4ui(final int a0, final int a1, final int a2, final int a3, final int a4) {
		this.gl.glVertexAttribI4ui(a0, a1, a2, a3, a4);
	}

	@Override
	public void glVertexAttribIPointer(final int a0, final int a1, final int a2, final int a3, final int a4) {
		this.gl.glVertexAttribIPointer(a0, a1, a2, a3, a4);
	}
}
