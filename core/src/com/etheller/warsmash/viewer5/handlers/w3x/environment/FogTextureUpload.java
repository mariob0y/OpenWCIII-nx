package com.etheller.warsmash.viewer5.handlers.w3x.environment;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.BufferUtils;

/**
 * Uploads tightly packed visibility rows, including map widths not divisible by four. warsmash-nx:
 * the texture's storage is made once (glTexImage2D) and then refilled through a buffer
 * (PboTextureUpload), which the console's GL does not wait for.
 */
final class FogTextureUpload {
    // LWJGL 2 requires capacity for a matrix even for scalar glGetInteger queries.
    private final IntBuffer previousAlignment = BufferUtils.newIntBuffer(16);
    private final com.etheller.warsmash.viewer5.gl.PboTextureUpload buffered = new com.etheller.warsmash.viewer5.gl.PboTextureUpload();
    private int allocatedWidth = -1;
    private int allocatedHeight = -1;

    void upload(GL30 gl, int width, int height, ByteBuffer pixels) {
        previousAlignment.clear();
        gl.glGetIntegerv(GL30.GL_UNPACK_ALIGNMENT, previousAlignment);
        gl.glPixelStorei(GL30.GL_UNPACK_ALIGNMENT, 1);
        try {
            if ((width != allocatedWidth) || (height != allocatedHeight)) {
                gl.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_R8, width, height, 0,
                        GL30.GL_RED, GL30.GL_UNSIGNED_BYTE, pixels);
                allocatedWidth = width;
                allocatedHeight = height;
            }
            else {
                buffered.upload(gl, width, height, GL30.GL_RED, GL30.GL_UNSIGNED_BYTE, pixels, width * height);
            }
        }
        finally {
            gl.glPixelStorei(GL30.GL_UNPACK_ALIGNMENT, previousAlignment.get(0));
        }
    }
}
