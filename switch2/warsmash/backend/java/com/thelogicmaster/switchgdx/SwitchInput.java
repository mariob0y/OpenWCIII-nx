package com.thelogicmaster.switchgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.input.NativeInputConfiguration;

import java.util.function.Consumer;

/**
 * warsmash-nx: replaces SwitchGDX's SwitchInput. One cursor, pointer 0, as a mouse: mouseMoved while no
 * button is held, touchDown/touchDragged/touchUp with Buttons.LEFT and Buttons.RIGHT. It moves with
 * the Joy-Con 2 mouse (libounce's PadPointer, read by getMouseData in backend/native/nx_switchgdx.cpp),
 * the right stick, the touch screen and setCursorPosition (the game's D-pad menu navigation).
 * Left = the Joy-Con mouse's own click, L, R or A; right = ZL or ZR. A finger moves the cursor to it;
 * dragging it past a few pixels holds the left button from where it went down, so a drag draws the
 * selection rectangle. The left stick is the camera stick (cameraStickX/Y). Pad buttons are also keys
 * (BUTTON_*, DPAD_*): the game reads + as its menu key and B as back. No software keyboard or dialog
 * (no applets on this console): text input is cancelled, confirmations answer null.
 */
public class SwitchInput implements Input {

	private static final int MAX_TOUCHES = 16;
	private static final int[] SWITCH_KEYS = new int[] {
		Keys.BUTTON_A, Keys.BUTTON_B, Keys.BUTTON_X, Keys.BUTTON_Y,
		Keys.DPAD_UP, Keys.DPAD_DOWN, Keys.DPAD_LEFT, Keys.DPAD_RIGHT,
		Keys.BUTTON_THUMBL, Keys.BUTTON_THUMBR, Keys.BUTTON_SELECT, Keys.BUTTON_START,
		Keys.BUTTON_L1, Keys.BUTTON_L2, Keys.BUTTON_R1, Keys.BUTTON_R2
	};
	// libnx's HidNpadButton bits (see keyToButton)
	private static final int BIT_A = 0, BIT_L = 6, BIT_R = 7, BIT_ZL = 8, BIT_ZR = 9;
	/** Stick tilt below this is rest. */
	private static final float STICK_DEAD_ZONE = 0.15f;
	/** Right stick at full tilt, in surface widths per second. */
	private static final float CURSOR_SPEED = 0.85f;
	/** The Joy-Con mouse's wheel is that Joy-Con's stick: no cursor from the stick this soon after it. */
	private static final long WHEEL_HOLDS_STICK_NANOS = 400_000_000L;
	/** A finger moved this far (surface pixels, x plus y) from where it went down is a drag. */
	private static final int TOUCH_DRAG_DISTANCE = 24;

	/**
	 * The left stick past its dead zone, -1..1, x right and y up: the camera stick. The launcher copies
	 * it to the game each frame (this backend is compiled before the game and cannot see it).
	 */
	public static volatile float cameraStickX, cameraStickY;

	private InputProcessor inputProcessor;
	private long currentEventTimeStamp;
	private final int[] touchData = new int[MAX_TOUCHES * 3];
	private final int[] touchX = new int[MAX_TOUCHES];
	private final int[] touchY = new int[MAX_TOUCHES];
	private final int[] deltaX = new int[MAX_TOUCHES];
	private final int[] deltaY = new int[MAX_TOUCHES];
	private final boolean[] touched = new boolean[MAX_TOUCHES];
	private boolean wasJustTouched;

	/** The cursor, surface pixels; touchX[0]/touchY[0] is where the game last saw it. */
	private float cursorX = -1, cursorY = -1;
	// Joy-Con mouse: active, x, y, its own click, the wheel's running total
	private final int[] mouseData = new int[5];
	private boolean pointerSeen;
	private int pointerX, pointerY, wheelTotal;
	private long lastWheelNanos;
	// the finger that drives the cursor
	private int fingerId = -1;
	private int fingerStartX, fingerStartY;
	private boolean fingerDragging;
	private boolean buttonLeft, buttonRight;
	private boolean justLeft, justRight;

	public SwitchInput () {
	}

	void update() {
		currentEventTimeStamp = System.nanoTime();
		wasJustTouched = false;
		justLeft = justRight = false;
		final SwitchController pad = (SwitchController)SwitchControllerManager.getInstance().getCurrentController();
		final int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
		if (cursorX < 0) {
			cursorX = width / 2f;
			cursorY = height / 2f;
			touchX[0] = (int) cursorX;
			touchY[0] = (int) cursorY;
		}

		// the Joy-Con mouse moves the cursor by its own movement, so the other ways of moving it
		// never make it jump
		getMouseData(mouseData);
		final boolean mouseActive = mouseData[0] != 0;
		if (mouseActive) {
			if (pointerSeen) {
				cursorX += mouseData[1] - pointerX;
				cursorY += mouseData[2] - pointerY;
			}
			pointerX = mouseData[1];
			pointerY = mouseData[2];
		}
		pointerSeen = mouseActive;
		final int wheel = mouseData[4] - wheelTotal;
		wheelTotal = mouseData[4];
		if (wheel != 0)
			lastWheelNanos = currentEventTimeStamp;

		// the right stick, unless it is the Joy-Con mouse's wheel just now
		if (currentEventTimeStamp - lastWheelNanos > WHEEL_HOLDS_STICK_NANOS) {
			final float rx = pad.getAxis(2), ry = pad.getAxis(3);
			final float tilt = stickTilt(rx, ry);
			if (tilt > 0) {
				final float length = (float) Math.sqrt(rx * rx + ry * ry);
				// squared: slow and precise near the centre, fast at full tilt
				final float step = CURSOR_SPEED * width * tilt * tilt * Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
				cursorX += rx / length * step;
				cursorY += ry / length * step;
			}
		}

		// the left stick pans the camera (the launcher hands it to the game)
		final float lx = pad.getAxis(0), ly = pad.getAxis(1);
		final float camera = stickTilt(lx, ly);
		final float cameraLength = (float) Math.sqrt(lx * lx + ly * ly);
		cameraStickX = camera > 0 ? lx / cameraLength * camera : 0;
		cameraStickY = camera > 0 ? -ly / cameraLength * camera : 0;

		// a finger puts the cursor where it is
		getTouchData(touchData);
		int finger = -1;
		for (int i = 0; i < MAX_TOUCHES; i++) {
			final int id = touchData[i * 3];
			if (id == -1)
				continue;
			if (fingerId == -1 || id == fingerId) {
				finger = i;
				break;
			}
		}
		boolean dragStarts = false;
		if (finger == -1) {
			fingerId = -1;
			fingerDragging = false;
		} else {
			final int fx = touchData[finger * 3 + 1], fy = touchData[finger * 3 + 2];
			if (fingerId == -1) {
				fingerId = touchData[finger * 3];
				fingerStartX = fx;
				fingerStartY = fy;
				fingerDragging = false;
			} else if (!fingerDragging && Math.abs(fx - fingerStartX) + Math.abs(fy - fingerStartY) > TOUCH_DRAG_DISTANCE) {
				fingerDragging = true;
				dragStarts = true;
			}
			cursorX = fx;
			cursorY = fy;
		}

		cursorX = Math.max(0, Math.min(width - 1, cursorX));
		cursorY = Math.max(0, Math.min(height - 1, cursorY));
		final boolean padLeft = (mouseActive && mouseData[3] != 0) || pad.getButton(BIT_A) || pad.getButton(BIT_L)
				|| pad.getButton(BIT_R);
		final boolean padRight = pad.getButton(BIT_ZL) || pad.getButton(BIT_ZR);

		// a drag goes down where the finger did, then follows it
		if (dragStarts && !buttonLeft) {
			moveCursorTo(fingerStartX, fingerStartY);
			setLeft(true);
		}
		moveCursorTo((int) cursorX, (int) cursorY);
		setLeft(padLeft || fingerDragging);
		setRight(padRight);
		touched[0] = buttonLeft || buttonRight;

		// gdx: amountY > 0 scrolls down. PadPointer's wheel (the Joy-Con's stick) is positive upward.
		if (wheel != 0 && inputProcessor != null)
			inputProcessor.scrolled(0, -Integer.signum(wheel));

		for (int key: SWITCH_KEYS) {
			int button = keyToButton(key);
			if (pad.getButtonPressed(button) && inputProcessor != null)
				inputProcessor.keyDown(key);
			if (pad.getButtonReleased(button) && inputProcessor != null)
				inputProcessor.keyUp(key);
		}
	}

	/** How far a stick is tilted past its dead zone, 0..1. */
	private static float stickTilt (float x, float y) {
		final float length = (float) Math.sqrt(x * x + y * y);
		if (length <= STICK_DEAD_ZONE)
			return 0;
		return Math.min(1, (length - STICK_DEAD_ZONE) / (1 - STICK_DEAD_ZONE));
	}

	private void moveCursorTo (int x, int y) {
		if (x == touchX[0] && y == touchY[0])
			return;
		deltaX[0] = x - touchX[0];
		deltaY[0] = y - touchY[0];
		touchX[0] = x;
		touchY[0] = y;
		if (inputProcessor != null) {
			if (buttonLeft || buttonRight)
				inputProcessor.touchDragged(x, y, 0);
			else
				inputProcessor.mouseMoved(x, y);
		}
	}

	private void setLeft (boolean down) {
		if (down == buttonLeft)
			return;
		buttonLeft = down;
		if (down) {
			justLeft = true;
			wasJustTouched = true;
		}
		if (inputProcessor != null) {
			if (down)
				inputProcessor.touchDown(touchX[0], touchY[0], 0, Buttons.LEFT);
			else
				inputProcessor.touchUp(touchX[0], touchY[0], 0, Buttons.LEFT);
		}
	}

	private void setRight (boolean down) {
		if (down == buttonRight)
			return;
		buttonRight = down;
		if (down)
			justRight = true;
		if (inputProcessor != null) {
			if (down)
				inputProcessor.touchDown(touchX[0], touchY[0], 0, Buttons.RIGHT);
			else
				inputProcessor.touchUp(touchX[0], touchY[0], 0, Buttons.RIGHT);
		}
	}

	@Override
	public float getAccelerometerX () {
		return 0;
	}

	@Override
	public float getAccelerometerY () {
		return 0;
	}

	@Override
	public float getAccelerometerZ () {
		return 0;
	}

	@Override
	public float getGyroscopeX () {
		return 0;
	}

	@Override
	public float getGyroscopeY () {
		return 0;
	}

	@Override
	public float getGyroscopeZ () {
		return 0;
	}

	@Override
	public int getMaxPointers () {
		return MAX_TOUCHES;
	}

	@Override
	public int getX () {
		return touchX[0];
	}

	@Override
	public int getX (int pointer) {
		return touchX[pointer];
	}

	@Override
	public int getDeltaX () {
		return deltaX[0];
	}

	@Override
	public int getDeltaX (int pointer) {
		return deltaX[pointer];
	}

	@Override
	public int getY () {
		return touchY[0];
	}

	@Override
	public int getY (int pointer) {
		return touchY[pointer];
	}

	@Override
	public int getDeltaY () {
		return deltaY[0];
	}

	@Override
	public int getDeltaY (int pointer) {
		return deltaY[pointer];
	}

	@Override
	public boolean isTouched () {
		return touched[0];
	}

	@Override
	public boolean justTouched () {
		return wasJustTouched;
	}

	@Override
	public boolean isTouched (int pointer) {
		return touched[pointer];
	}

	@Override
	public float getPressure () {
		return getPressure(0);
	}

	@Override
	public float getPressure (int pointer) {
		return isTouched(pointer) ? 1 : 0;
	}

	@Override
	public boolean isButtonPressed (int button) {
		if (button == Buttons.LEFT)
			return buttonLeft;
		return button == Buttons.RIGHT && buttonRight;
	}

	@Override
	public boolean isButtonJustPressed (int button) {
		if (button == Buttons.LEFT)
			return justLeft;
		return button == Buttons.RIGHT && justRight;
	}

	@Override
	public boolean isKeyPressed (int key) {
		if (key == Keys.ANY_KEY)
			return ((SwitchController)Controllers.getCurrent()).isAnyButtonPressed();
		int button = keyToButton(key);
		return button != -1 && Controllers.getCurrent().getButton(button);
	}

	@Override
	public boolean isKeyJustPressed (int key) {
		if (key == Keys.ANY_KEY)
			return ((SwitchController)Controllers.getCurrent()).isAnyButtonJustPressed();
		int button = keyToButton(key);
		return button != -1 && ((SwitchController)Controllers.getCurrent()).getButtonPressed(button);
	}

	@Override
	public void getTextInput (TextInputListener listener, String title, String text, String hint) {
		getTextInput(listener, title, text, hint, OnscreenKeyboardType.Default);
	}

	@Override
	public void getTextInput (TextInputListener listener, String title, String text, String hint, OnscreenKeyboardType type) {
		Gdx.app.postRunnable(listener::canceled);
	}
	
	@Override
	public void setOnscreenKeyboardVisible (boolean visible) {

	}

	@Override
	public void setOnscreenKeyboardVisible (boolean visible, OnscreenKeyboardType type) {

	}

	@Override
	public void openTextInputField(NativeInputConfiguration nativeInputConfiguration) {

	}

	@Override
	public void closeTextInputField(boolean b) {

	}

	@Override
	public void setKeyboardHeightObserver(KeyboardHeightObserver keyboardHeightObserver) {

	}

	@Override
	public void vibrate (int milliseconds) {

	}

	@Override
	public void vibrate(int milliseconds, boolean fallback) {

	}

	@Override
	public void vibrate(int milliseconds, int amplitude, boolean fallback) {

	}

	@Override
	public void vibrate(VibrationType vibrationType) {

	}

	@Override
	public float getAzimuth () {
		return 0;
	}

	@Override
	public float getPitch () {
		return 0;
	}

	@Override
	public float getRoll () {
		return 0;
	}

	@Override
	public void getRotationMatrix (float[] matrix) {

	}

	@Override
	public long getCurrentEventTime () {
		return currentEventTimeStamp;
	}

	@Override
	public void setCatchKey (int keycode, boolean catchKey) { }

	@Override
	public boolean isCatchKey (int keycode) {
		return false;
	}

	@Override
	public void setInputProcessor (InputProcessor processor) {
		inputProcessor = processor;
	}

	@Override
	public InputProcessor getInputProcessor () {
		return inputProcessor;
	}

	@Override
	public boolean isPeripheralAvailable (Peripheral peripheral) {
		switch (peripheral) {
		case MultitouchScreen:
			return true;
		default:
			return false;
		}
	}

	@Override
	public int getRotation () {
		return 0;
	}

	@Override
	public Orientation getNativeOrientation () {
		return Orientation.Landscape;
	}

	@Override
	public void setCursorCatched (boolean catched) {

	}

	@Override
	public boolean isCursorCatched () {
		return false;
	}

	/**
	 * Moves the cursor without an event (the caller sends its own mouseMoved) and puts the Joy-Con
	 * mouse's pointer there too, so moving the Joy-Con carries on from here.
	 */
	@Override
	public void setCursorPosition (int x, int y) {
		cursorX = Math.max(0, Math.min(Gdx.graphics.getWidth() - 1, x));
		cursorY = Math.max(0, Math.min(Gdx.graphics.getHeight() - 1, y));
		deltaX[0] = (int) cursorX - touchX[0];
		deltaY[0] = (int) cursorY - touchY[0];
		touchX[0] = (int) cursorX;
		touchY[0] = (int) cursorY;
		setMousePosition(touchX[0], touchY[0]);
		getMouseData(mouseData);
		if (mouseData[0] != 0) {
			pointerSeen = true;
			pointerX = mouseData[1];
			pointerY = mouseData[2];
		}
	}

	private static int keyToButton(int key) {
		switch (key) {
		case Keys.BUTTON_A:
			return 0;
		case Keys.BUTTON_B:
			return 1;
		case Keys.BUTTON_X:
			return 2;
		case Keys.BUTTON_Y:
			return 3;
		// warsmash-nx: libnx's bits are Plus = 10, Minus = 11 (HidNpadButton_Plus = BIT(10)); switch-gdx
		// had them the other way round, so START (+) read the Minus button.
		case Keys.BUTTON_START:
			return 10;
		case Keys.BUTTON_SELECT:
			return 11;
		case Keys.DPAD_UP:
			return 13;
		case Keys.DPAD_RIGHT:
			return 14;
		case Keys.DPAD_DOWN:
			return 15;
		case Keys.DPAD_LEFT:
			return 12;
		case Keys.BUTTON_THUMBL:
			return 4;
		case Keys.BUTTON_THUMBR:
			return 5;
		case Keys.BUTTON_L1:
			return 6;
		case Keys.BUTTON_L2:
			return 8;
		case Keys.BUTTON_R1:
			return 7;
		case Keys.BUTTON_R2:
			return 9;
		default:
			return -1;
		}
	}

	public void showConfirm(String title, String message, Consumer<String> handler, String ...buttons) {
		Gdx.app.postRunnable(() -> {
			if (handler != null)
				handler.accept(null);
		});
	}

	/** The Joy-Con mouse: active, x, y (surface pixels), its own click, the wheel's running total. */
	private static native void getMouseData(int[] mouseData);

	/** Puts the Joy-Con mouse's pointer at x, y (surface pixels). */
	private static native void setMousePosition(int x, int y);

	private static native void getTouchData(int[] touchData);
}
