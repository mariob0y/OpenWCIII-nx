package com.etheller.warsmash.viewer5.gl;

import java.nio.ByteBuffer;

import com.badlogic.gdx.graphics.GL30;

/**
 * warsmash-nx: replaces the whole of a 2D texture's level 0 from memory through a pixel unpack
 * buffer. On the console (Mesa's threaded context over zink) a texture upload larger than a few
 * hundred bytes straight from memory makes the game thread wait until the driver thread has run
 * every GL call queued so far: the minimap's fog cost 11.5% of the game thread that way (u23), new
 * storage or not. Filling a buffer with glBufferData is queued like any call (it replaces the
 * buffer's storage), and the copy from the buffer into the texture runs on the GPU.
 */
public final class PboTextureUpload {
	private int buffer;

	/**
	 * Uploads {@code pixels} (from its position, {@code bytes} of it) into the texture bound to
	 * GL_TEXTURE_2D, which must already have level 0 of {@code width} x {@code height} (glTexImage2D).
	 * The unpack state (alignment) applies as for any upload.
	 */
	public void upload(final GL30 gl, final int width, final int height, final int format, final int type,
			final ByteBuffer pixels, final int bytes) {
		if (this.buffer == 0) {
			this.buffer = gl.glGenBuffer();
		}
		final ByteBuffer data = pixels.duplicate();
		data.limit(data.position() + bytes);
		gl.glBindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, this.buffer);
		gl.glBufferData(GL30.GL_PIXEL_UNPACK_BUFFER, bytes, data, GL30.GL_STREAM_DRAW);
		gl.glTexSubImage2D(GL30.GL_TEXTURE_2D, 0, 0, 0, width, height, format, type, 0);
		gl.glBindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, 0);
	}

	public void dispose(final GL30 gl) {
		if (this.buffer != 0) {
			gl.glDeleteBuffer(this.buffer);
			this.buffer = 0;
		}
	}
}
