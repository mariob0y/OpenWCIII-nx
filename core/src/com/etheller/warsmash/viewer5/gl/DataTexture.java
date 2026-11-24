package com.etheller.warsmash.viewer5.gl;

import java.nio.Buffer;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;

public class DataTexture {
	public GL20 gl;
	public int texture;
	public int format;
	public int internalFormat;
	public int width = 0;
	public int height = 0;
	private final int channels;
	private int unpackBuffer;

	public DataTexture(final GL20 gl, final int channels, final int width, final int height) {
		this.gl = gl;
		this.channels = channels;
		this.texture = gl.glGenTexture();
		this.format = (channels == 3 ? GL20.GL_RGB : GL20.GL_RGBA);
		this.internalFormat = (channels == 3 ? GL20.GL_RGB : GL30.GL_RGBA32F);

		gl.glBindTexture(GL20.GL_TEXTURE_2D, this.texture);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);

		this.reserve(width, height);
	}

	public void reserve(final int width, final int height) {
		if ((this.width < width) || (this.height < height)) {
			final GL20 gl = this.gl;

			this.width = Math.max(this.width, width);
			this.height = Math.max(this.height, height);

			gl.glBindTexture(GL20.GL_TEXTURE_2D, this.texture);
			gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, this.internalFormat, this.width, this.height, 0, this.format,
					GL20.GL_FLOAT, null);
		}

	}

	public void bindAndUpdate(final Buffer buffer) {
		bindAndUpdate(buffer, this.width, this.height);
	}

	public void bindAndUpdate(final Buffer buffer, final int width, final int height) {
		if ((width <= 0) || (height <= 0)) {
			return;
		}
		final GL20 gl = this.gl;

		if (gl instanceof GL30) {
			// warsmash-nx: upload via a streaming PBO so Mesa/Zink's threaded context queues the
			// transfer asynchronously instead of calling _tc_sync() on every >320B CPU upload.
			final GL30 gl30 = (GL30) gl;
			if (this.unpackBuffer == 0) {
				this.unpackBuffer = gl30.glGenBuffer();
			}
			final int bytes = width * height * this.channels * 4;
			final int elementsNeeded = (buffer instanceof java.nio.ByteBuffer) ? bytes : (width * height * this.channels);
			final int oldLimit = buffer.limit();
			final int targetLimit = buffer.position() + elementsNeeded;
			final boolean adjustedLimit = (targetLimit <= buffer.capacity()) && (targetLimit != oldLimit);
			if (adjustedLimit) {
				buffer.limit(targetLimit);
			}
			gl30.glBindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, this.unpackBuffer);
			gl30.glBufferData(GL30.GL_PIXEL_UNPACK_BUFFER, bytes, buffer, GL20.GL_STREAM_DRAW);
			gl30.glBindTexture(GL20.GL_TEXTURE_2D, this.texture);
			gl30.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, width, height, this.format, GL20.GL_FLOAT, 0);
			gl30.glBindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, 0);
			if (adjustedLimit) {
				buffer.limit(oldLimit);
			}
			return;
		}

		gl.glBindTexture(GL20.GL_TEXTURE_2D, this.texture);
		gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, width, height, this.format, GL20.GL_FLOAT, buffer);
	}

	public void bind(final int unit) {
		final GL20 gl = this.gl;

		gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
		gl.glBindTexture(GL20.GL_TEXTURE_2D, this.texture);
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}
}
