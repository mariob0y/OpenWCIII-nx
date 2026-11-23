package wsnx.warsmash;

import com.badlogic.gdx.audio.Sound;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.AudioDestination;
import com.etheller.warsmash.viewer5.gl.ANGLEInstancedArrays;
import com.etheller.warsmash.viewer5.gl.AudioExtension;
import com.etheller.warsmash.viewer5.gl.DynamicShadowExtension;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.gl.WireframeExtension;
import com.thelogicmaster.switchgdx.SwitchSound;

/**
 * The console's version of DesktopLauncher.loadExtensions: the GL 3.x calls Warsmash makes outside
 * GL20/GL30, bound to the desktop GL 3.3 core context (zink) through eglGetProcAddress, as probe A
 * loads its functions. Audio: no positional sound yet (W3); sounds play through gdx's Sound.
 */
public final class NxExtensions {
	private NxExtensions() {
	}

	/*JNI
	#include <EGL/egl.h>

	namespace {
	template <typename F> F nxGlProc(F &cached, const char *name) {
		if (!cached)
			cached = reinterpret_cast<F>(eglGetProcAddress(name));
		return cached;
	}
	}
	*/

	// GL constants, from glcorearb.h (Warsmash takes them from LWJGL's GL11 on the desktop).
	static final int GL_LINE = 0x1B01;
	static final int GL_FILL = 0x1B02;

	/** The world audio context's listener (the camera); 3D sounds are placed against it. */
	static WorldListener worldListener;

	/**
	 * Positional audio, simplified from desktop's OpenAL: a sound's gain and pan are computed once,
	 * when it starts (desktop moves it with the camera), with OpenAL's default model
	 * (AL_INVERSE_DISTANCE_CLAMPED, rolloff 1): gain = ref / (ref + (clamp(d, ref, max) - ref)).
	 * Pan is the direction along the listener's right (forward x up).
	 */
	static final class WorldListener implements AudioContext.Listener {
		private float x, y, z;
		private float rightX = 1, rightY, rightZ;

		@Override
		public float getX() {
			return x;
		}

		@Override
		public float getY() {
			return y;
		}

		@Override
		public float getZ() {
			return z;
		}

		@Override
		public void setPosition(final float x, final float y, final float z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public void setOrientation(final float forwardX, final float forwardY, final float forwardZ, final float upX,
				final float upY, final float upZ) {
			float rx = forwardY * upZ - forwardZ * upY;
			float ry = forwardZ * upX - forwardX * upZ;
			float rz = forwardX * upY - forwardY * upX;
			final float length = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
			if (length > 1e-6f) {
				rightX = rx / length;
				rightY = ry / length;
				rightZ = rz / length;
			}
		}

		@Override
		public boolean is3DSupported() {
			return true;
		}

		float gain(final float sx, final float sy, final float sz, final float refDistance, final float maxDistance) {
			if (refDistance <= 0) {
				return 1;
			}
			final float dx = sx - x, dy = sy - y, dz = sz - z;
			float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			d = Math.max(refDistance, Math.min(d, Math.max(refDistance, maxDistance)));
			return refDistance / (refDistance + (d - refDistance));
		}

		float pan(final float sx, final float sy, final float sz) {
			final float dx = sx - x, dy = sy - y, dz = sz - z;
			final float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d < 1e-3f) {
				return 0;
			}
			return Math.max(-1, Math.min(1, (dx * rightX + dy * rightY + dz * rightZ) / d));
		}
	}

	public static void install() {
		Extensions.angleInstancedArrays = new ANGLEInstancedArrays() {
			@Override
			public void glVertexAttribDivisorANGLE(final int index, final int divisor) {
				vertexAttribDivisor(index, divisor);
			}

			@Override
			public void glDrawElementsInstancedANGLE(final int mode, final int count, final int type,
					final int indicesOffset, final int instanceCount) {
				drawElementsInstanced(mode, count, type, indicesOffset, instanceCount);
			}

			@Override
			public void glDrawArraysInstancedANGLE(final int mode, final int first, final int count,
					final int instanceCount) {
				drawArraysInstanced(mode, first, count, instanceCount);
			}
		};
		Extensions.dynamicShadowExtension = new DynamicShadowExtension() {
			@Override
			public void glFramebufferTexture(final int target, final int attachment, final int texture,
					final int level) {
				framebufferTexture(target, attachment, texture, level);
			}

			@Override
			public void glDrawBuffer(final int mode) {
				drawBuffer(mode);
			}
		};
		Extensions.wireframeExtension = new WireframeExtension() {
			@Override
			public void glPolygonMode(final int face, final int mode) {
				polygonMode(face, mode);
			}
		};
		Extensions.audio = new AudioExtension() {
			@Override
			public float getDuration(final Sound sound) {
				return sound instanceof SwitchSound ? ((SwitchSound) sound).duration() : 1;
			}

			@Override
			public long play(final Sound buffer, final float volume, final float pitch, final float x, final float y,
					final float z, final boolean is3dSound, final float maxDistance, final float refDistance,
					final boolean looping) {
				if (buffer == null) {
					return -1;
				}
				float gain = volume;
				float pan = 0;
				if (is3dSound && worldListener != null) {
					gain *= worldListener.gain(x, y, z, refDistance, maxDistance);
					pan = worldListener.pan(x, y, z);
				}
				return looping ? buffer.loop(gain, pitch, pan) : buffer.play(gain, pitch, pan);
			}

			@Override
			public AudioContext createContext(final boolean world) {
				final AudioContext.Listener listener;
				if (world) {
					worldListener = new WorldListener();
					listener = worldListener;
				}
				else {
					listener = AudioContext.Listener.DO_NOTHING;
				}
				return new AudioContext(listener, new AudioDestination() {
				});
			}
		};
		Extensions.GL_LINE = GL_LINE;
		Extensions.GL_FILL = GL_FILL;
	}

	private static native void vertexAttribDivisor(int index, int divisor); /*
		static void (*f)(unsigned, unsigned);
		nxGlProc(f, "glVertexAttribDivisor")((unsigned) index, (unsigned) divisor);
	*/

	private static native void drawElementsInstanced(int mode, int count, int type, int indicesOffset,
			int instanceCount); /*
		static void (*f)(unsigned, int, unsigned, const void *, int);
		nxGlProc(f, "glDrawElementsInstanced")((unsigned) mode, count, (unsigned) type,
				(const void *) (intptr_t) indicesOffset, instanceCount);
	*/

	private static native void drawArraysInstanced(int mode, int first, int count, int instanceCount); /*
		static void (*f)(unsigned, int, int, int);
		nxGlProc(f, "glDrawArraysInstanced")((unsigned) mode, first, count, instanceCount);
	*/

	private static native void framebufferTexture(int target, int attachment, int texture, int level); /*
		static void (*f)(unsigned, unsigned, unsigned, int);
		nxGlProc(f, "glFramebufferTexture")((unsigned) target, (unsigned) attachment, (unsigned) texture, level);
	*/

	private static native void drawBuffer(int mode); /*
		static void (*f)(unsigned);
		nxGlProc(f, "glDrawBuffer")((unsigned) mode);
	*/

	private static native void polygonMode(int face, int mode); /*
		static void (*f)(unsigned, unsigned);
		nxGlProc(f, "glPolygonMode")((unsigned) face, (unsigned) mode);
	*/
}
