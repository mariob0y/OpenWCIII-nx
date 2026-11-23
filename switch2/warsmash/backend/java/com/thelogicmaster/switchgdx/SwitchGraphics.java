package com.thelogicmaster.switchgdx;

import com.badlogic.gdx.AbstractGraphics;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.GLVersion;

/**
 * warsmash-nx: replaces SwitchGDX's SwitchGraphics. The context is desktop GL 3.3 core (zink), as
 * Warsmash's desktop launcher asks (useGL30, 3.3), so GL30 is available: Gdx.gl30 is set (Warsmash
 * makes nearly every GL call through it, and gdx takes its VAO paths). In a core profile
 * glGetString(GL_EXTENSIONS) is an error (upstream got null and supportsExtension would throw): the
 * list is read with glGetStringi. Frame time from System.nanoTime (was milliseconds).
 */
public class SwitchGraphics extends AbstractGraphics {

	private final BufferFormat bufferFormat;
	private final GLVersion glVersion;
	private final String extensions;

	private float time;
	private float deltaTime;
	private static final float MAX_DELTA_TIME = 0.25f;
	private long frameId = -1;
	private int frames;
	private long previousTime;
	private int fps;

	private final wsnx.nx.NxGL30 gl30;

	public SwitchGraphics () {
		gl30 = new wsnx.nx.NxGL30();
		Gdx.gl = Gdx.gl20 = Gdx.gl30 = gl30;

		String versionString = Gdx.gl.glGetString(GL20.GL_VERSION);
		String vendorString = Gdx.gl.glGetString(GL20.GL_VENDOR);
		String rendererString = Gdx.gl.glGetString(GL20.GL_RENDERER);
		glVersion = new GLVersion(Application.ApplicationType.Desktop, versionString, vendorString, rendererString);

		final java.nio.IntBuffer count = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16);
		gl30.glGetIntegerv(com.badlogic.gdx.graphics.GL30.GL_NUM_EXTENSIONS, count);
		final StringBuilder names = new StringBuilder();
		for (int i = 0; i < count.get(0); i++)
			names.append(gl30.glGetStringi(GL20.GL_EXTENSIONS, i)).append(' ');
		extensions = names.toString();

		bufferFormat = new BufferFormat(8, 8, 8, 8, 24, 8, 0, false);

		previousTime = System.nanoTime();
	}

	void update() {
		long timestamp = System.nanoTime();
		final float elapsed = (Math.max(timestamp - previousTime, 1000)) / 1e9f;
		// warsmash-nx: the first frame after loading came 109 s after the previous one (loading
		// runs inside create()), and the menu died animating that step on the console. A frame
		// counts as at most MAX_DELTA_TIME: animations and particles slow down below 4 fps rather
		// than jump.
		deltaTime = Math.min(elapsed, MAX_DELTA_TIME);
		previousTime = timestamp;
		time += elapsed;
		frameId++;
		frames++;
		if (time > 1) {
			fps = frames;
			time = 0;
			frames = 0;
		}
	}

	@Override
	public boolean isGL30Available () {
		return true;
	}

	@Override
	public boolean isGL31Available() {
		return false;
	}

	@Override
	public boolean isGL32Available() {
		return false;
	}

	@Override
	public GL20 getGL20 () {
		return Gdx.gl20;
	}

	@Override
	public GL30 getGL30 () {
		return gl30;
	}

	@Override
	public GL31 getGL31() {
		return null;
	}

	@Override
	public GL32 getGL32() {
		return null;
	}

	@Override
	public void setGL20 (GL20 gl20) {
		Gdx.gl = gl20;
		Gdx.gl20 = gl20;
	}
	// setGL30 below: Gdx.gl30 stays the context's (a GL30 can only be this one).

	@Override
	public void setGL30 (GL30 gl30) {

	}

	@Override
	public void setGL31(GL31 gl31) {

	}

	@Override
	public void setGL32(GL32 gl32) {

	}

	@Override
	public native int getWidth ();

	@Override
	public native int getHeight ();

	@Override
	public int getBackBufferWidth () {
		return getWidth();
	}

	@Override
	public int getBackBufferHeight () {
		return getHeight();
	}

	@Override
	public int getSafeInsetLeft () {
		return 0;
	}

	@Override
	public int getSafeInsetTop () {
		return 0;
	}

	@Override
	public int getSafeInsetBottom () {
		return 0;
	}

	@Override
	public int getSafeInsetRight () {
		return 0;
	}

	@Override
	public long getFrameId () {
		return frameId;
	}

	@Override
	public float getDeltaTime () {
		return deltaTime;
	}

	@Override
	public int getFramesPerSecond () {
		return fps;
	}

	@Override
	public GraphicsType getType () {
		return GraphicsType.LWJGL;
	}

	@Override
	public GLVersion getGLVersion () {
		return glVersion;
	}

	@Override
	public float getPpiX () {
		return 160;
	}

	@Override
	public float getPpiY () {
		return 160;
	}

	@Override
	public float getPpcX () {
		return 63;
	}

	@Override
	public float getPpcY () {
		return 63;
	}

	@Override
	public boolean supportsDisplayModeChange () {
		return false;
	}

	@Override
	public Monitor getPrimaryMonitor () {
		return getMonitor();
	}

	@Override
	public Monitor getMonitor () {
		return new SwitchMonitor(0, 0, "Switch Display");
	}

	@Override
	public Monitor[] getMonitors () {
		return new Monitor[]{getMonitor()};
	}

	@Override
	public DisplayMode[] getDisplayModes () {
		return new DisplayMode[]{getDisplayMode()};
	}

	@Override
	public DisplayMode[] getDisplayModes (Monitor monitor) {
		return new DisplayMode[]{getDisplayMode()};
	}

	@Override
	public DisplayMode getDisplayMode () {
		return new SwitchDisplayMode(getWidth(), getHeight(), 60, 32);
	}

	@Override
	public DisplayMode getDisplayMode (Monitor monitor) {
		return getDisplayMode();
	}

	@Override
	public boolean setFullscreenMode (DisplayMode displayMode) {
		return false;
	}

	@Override
	public boolean setWindowedMode (int width, int height) {
		return false;
	}

	@Override
	public void setTitle (String title) {

	}

	@Override
	public void setUndecorated (boolean undecorated) {

	}

	@Override
	public void setResizable (boolean resizable) {

	}

	@Override
	public void setVSync (boolean vsync) {

	}

	@Override
	public void setForegroundFPS (int fps) {

	}

	@Override
	public BufferFormat getBufferFormat () {
		return bufferFormat;
	}

	@Override
	public boolean supportsExtension (String extension) {
		return extensions.contains(extension);
	}

	@Override
	public void setContinuousRendering (boolean isContinuous) {

	}

	@Override
	public boolean isContinuousRendering () {
		return true;
	}

	@Override
	public void requestRendering () {

	}

	@Override
	public boolean isFullscreen () {
		return true;
	}

	@Override
	public Cursor newCursor (Pixmap pixmap, int xHotspot, int yHotspot) {
		return null;
	}

	@Override
	public void setCursor (Cursor cursor) {

	}

	@Override
	public void setSystemCursor (Cursor.SystemCursor systemCursor) {

	}

	static class SwitchDisplayMode extends DisplayMode {
		public SwitchDisplayMode (int width, int height, int refreshRate, int bitsPerPixel) {
			super(width, height, refreshRate, bitsPerPixel);
		}
	}

	static class SwitchMonitor extends Monitor {
		protected SwitchMonitor (int virtualX, int virtualY, String name) {
			super(virtualX, virtualY, name);
		}
	}
}
