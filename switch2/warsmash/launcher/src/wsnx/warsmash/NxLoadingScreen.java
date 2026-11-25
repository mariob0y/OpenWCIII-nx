package wsnx.warsmash;

import java.io.File;
import java.nio.IntBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import com.etheller.warsmash.util.LoadingStatus;
import com.etheller.warsmash.util.ParallelJobs;

/**
 * warsmash-nx: the loading screen. Warsmash loads everything inside create()/show(), before
 * SwitchApplication's loop draws its first frame: minutes of black screen on the console. Warsmash
 * reports its steps to {@link LoadingStatus}; this draws the step, its progress and the elapsed time
 * in Warcraft III's gold Friz Quadrata (from the game's own files on the card) and presents the frame
 * itself (nx_present_loading_frame, which also answers HOME and + and - held together). At most four
 * frames a second, only on the thread that made it (the GL thread), and nothing after done().
 *
 * Each step is logged with its start time and, when the next one starts, its duration and last
 * progress, so warsmash.log shows where a boot spends its time.
 *
 * Drawn with its own GLSL 3.30 shader: gdx's default SpriteBatch shader has no #version (GLSL 1.10),
 * which Mesa refuses in a core-profile context.
 */
public final class NxLoadingScreen implements LoadingStatus.Listener {
	/*JNI
	#include "java/lang/Thread.h"
	extern "C" void nx_present_loading_frame();
	#include <switch.h>
	extern "C" unsigned long long nx_start_tick;
	extern "C" void nxLogThreadLoad(unsigned long long wallTicks);
	*/

	private static final String FONT = "sdmc:/switch/warsmash/wc3data/war3.w3mod/_locales/enus.w3mod/fonts/frizqt__.ttf";
	private static final long FRAME_NS = 250000000L;

	private static final Color GOLD = new Color(1f, 0.8f, 0f, 1f);
	private static final Color PALE = new Color(0.85f, 0.85f, 0.85f, 1f);
	private static final Color DIM = new Color(0.62f, 0.5f, 0.18f, 1f);
	private static final Color RED = new Color(1f, 0.32f, 0.25f, 1f);
	private static final long ERROR_SHOWN_NS = 20000000000L;

	private static final String VERTEX = "#version 330 core\n" //
			+ "in vec4 a_position;\n" //
			+ "in vec4 a_color;\n" //
			+ "in vec2 a_texCoord0;\n" //
			+ "uniform mat4 u_projTrans;\n" //
			+ "out vec4 v_color;\n" //
			+ "out vec2 v_texCoords;\n" //
			+ "void main() {\n" //
			+ "  v_color = a_color;\n" //
			+ "  v_color.a = v_color.a * (255.0 / 254.0);\n" //
			+ "  v_texCoords = a_texCoord0;\n" //
			+ "  gl_Position = u_projTrans * a_position;\n" //
			+ "}\n";
	private static final String FRAGMENT = "#version 330 core\n" //
			+ "in vec4 v_color;\n" //
			+ "in vec2 v_texCoords;\n" //
			+ "uniform sampler2D u_texture;\n" //
			+ "out vec4 fragColor;\n" //
			+ "void main() {\n" //
			+ "  fragColor = v_color * texture(u_texture, v_texCoords);\n" //
			+ "}\n";

	private final Thread glThread = Thread.currentThread();
	private final long startNs = System.nanoTime();
	private volatile String step = "";
	private volatile String detail = "";
	private volatile long stepStartNs = this.startNs;
	private long lastFrameNs;
	private boolean active = true;
	private boolean failed;

	private ShaderProgram shader;
	private SpriteBatch batch;
	private BitmapFont bigFont;
	private BitmapFont smallFont;
	private GlyphLayout layout;
	private IntBuffer intQuery;

	private static native void present(); /*
		nx_present_loading_frame();
	*/

	/**
	 * Prints the innermost frames of a thread's Java stack (method:line) to the log. Read without
	 * stopping that thread, so a slot can be half-written: FrameInfo records and their method names
	 * are static, i.e. in the program's own code/data mappings, and any pointer elsewhere is shown as
	 * "?" instead of read (2026-11-22: a dump during the OrcX01 intro printed a garbage name and the
	 * game died in the next one). Asked of the kernel (svcQueryMemory): libnx's __start__ is an
	 * absolute 0, not the load address, so a range check against it refused every pointer.
	 */
	private static native void dumpStack(Thread thread); /*
		auto inImage = [](const void *p) {
			if (!p)
				return false;
			MemoryInfo info;
			u32 pageInfo;
			if (R_FAILED(svcQueryMemory(&info, &pageInfo, (u64) p)) || !(info.perm & Perm_R))
				return false;
			const u32 type = info.type & MemState_Type;
			return type == MemType_CodeStatic || type == MemType_CodeMutable
					|| type == MemType_ModuleCodeStatic || type == MemType_ModuleCodeMutable;
		};
		auto target = (jcontext) ((java_lang_Thread *) thread)->F_nativeContext;
		if (!target) {
			printf("loading:   (no stack for that thread)");
			putchar(10);
			fflush(stdout);
			return;
		}
		int depth = target->stackDepth;
		if (depth > MAX_STACK_DEPTH)
			depth = MAX_STACK_DEPTH;
		printf("loading:     %d frames, innermost first:", depth);
		putchar(10);
		for (int i = depth - 1, shown = 0; i >= 0 && shown < 48; i--, shown++) {
			const FrameInfo *info = target->frames[i].info;
			const char *method = inImage(info) ? info->method : nullptr;
			printf("loading:     %s:%d", inImage(method) ? method : "?", target->frames[i].lineNumber);
			putchar(10);
		}
		fflush(stdout);
	*/

	/**
	 * The CPU boost of the system's loading screens (ApmCpuBoostMode_FastLoad: CPU up, GPU down) while
	 * a loading step runs, off as soon as none does (left on, it holds the GPU down in play). As the
	 * Torchlight port's set_loading_boost (platform_frame.cpp): once refused, not asked again.
	 */
	private static native void setLoadingBoost(boolean on); /*
		static bool active = false;
		static bool refused = false;
		if (on == active || refused)
			return;
		const Result rc = appletSetCpuBoostMode(on ? ApmCpuBoostMode_FastLoad : ApmCpuBoostMode_Normal);
		if (R_FAILED(rc)) {
			if (on) {
				refused = true;
				printf("loading: cpu boost unavailable (0x%x)\n", (unsigned) rc);
			} else {
				printf("loading: cpu boost would not switch off (0x%x)\n", (unsigned) rc);
			}
			return;
		}
		active = on;
		printf("loading: cpu boost %s\n", on ? "on" : "off");
	*/

	/** Seconds since the program's first own code (userAppInit, nx_switchgdx.cpp). */
	private static native double secondsSinceStart(); /*
		return (double) armTicksToNs(armGetSystemTick() - nx_start_tick) / 1e9;
	*/

	/** A step this long gets the game thread's stack in the log, and again every as long. */
	private static final long STALL_NS = 20000000000L;

	/**
	 * Diagnostics: which of {@code candidates} owns {@code target}'s monitor (index), -1 for nobody, -2 for
	 * a thread not among them. Also logs how many threads are parked waiting for it.
	 */
	private static native int monitorOwner(Object target, Thread[] candidates); /*
		auto held = (jobject) target;
		auto monitor = (jmonitor) __atomic_load_n(&held->monitor, __ATOMIC_ACQUIRE);
		if (!monitor)
			return -1;
		printf("loading:   monitor: %d thread(s) parked waiting for it", (int) monitor->waiters.load());
		putchar(10);
		fflush(stdout);
		auto owner = monitor->owner.load();
		if (!owner)
			return -1;
		auto list = (jarray) candidates;
		for (int i = 0; i < list->length; i++) {
			auto candidate = (java_lang_Thread *) ((jobject *) list->data)[i];
			if (candidate && (jcontext) candidate->F_nativeContext == owner)
				return i;
		}
		return -2;
	*/

	/** Diagnostics: the VM's view of a thread (alive, stopped for the collector, blocked, stack depth). */
	private static native String threadState(Thread thread); /*
		auto javaThread = (java_lang_Thread *) thread;
		auto target = (jcontext) javaThread->F_nativeContext;
		char text[200];
		if (!target)
			snprintf(text, sizeof text, "no VM context (alive %d)", (int) javaThread->F_alive);
		else
			snprintf(text, sizeof text, "alive %d, parked or at a safepoint %d, blocked on a monitor %s, Java stack depth %d",
					(int) javaThread->F_alive, (int) target->suspended, target->blockedBy.load() ? "yes" : "no",
					target->stackDepth);
		return (jobject) stringFromNative(ctx, text);
	*/

	/** Diagnostics: each thread's share of the CPU since the last dump ([NxThreads], nx_threads.cpp). */
	private static native void logThreadLoad(); /*
		static unsigned long long last = 0;
		const unsigned long long now = armGetSystemTick();
		if (last != 0)
			nxLogThreadLoad(now - last);
		last = now;
		fflush(stdout);
	*/

	/** Diagnostics for a stall: the job pool, who holds its monitor, and every worker's stack. */
	private void describeWorkers() {
		final Thread[] workers = ParallelJobs.workerThreads();
		final Thread[] all = new Thread[workers.length + 1];
		all[0] = this.glThread;
		System.arraycopy(workers, 0, all, 1, workers.length);
		System.out.println("loading:   job pool: " + ParallelJobs.describe());
		System.out.flush();
		final int owner = monitorOwner(ParallelJobs.monitor(), all);
		System.out.println("loading:   job pool monitor held by: "
				+ ((owner == -1) ? "nobody" : (owner == -2) ? "a thread outside the pool" : all[owner].getName()));
		System.out.println("loading:   game thread: " + threadState(this.glThread));
		System.out.flush();
		logThreadLoad();
		for (final Thread worker : workers) {
			System.out.println("loading:   " + worker.getName() + ": " + threadState(worker));
			System.out.flush();
			dumpStack(worker);
		}
	}

	/** Installs the screen as Warsmash's listener and shows the first step at once. */
	public static NxLoadingScreen install(final String firstStep) {
		final NxLoadingScreen screen = new NxLoadingScreen();
		System.out.println("loading: the loading screen starts " + seconds((long) (secondsSinceStart() * 1e9))
				+ " after the program did");
		LoadingStatus.setListener(screen);
		screen.status(firstStep, "");
		final Thread watchdog = new Thread(screen::watch, "loading-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		return screen;
	}

	/**
	 * While a step runs longer than STALL_NS: logs that, with where the game thread is, every
	 * STALL_NS. Tells a slow step (the stack moves) from a stuck one (it does not), and where.
	 */
	private void watch() {
		String dumpedStep = null;
		long dumpedNs = 0;
		while (true) {
			try {
				Thread.sleep(5000);
			}
			catch (final InterruptedException e) {
				return;
			}
			final String current = this.step;
			final long started = this.stepStartNs;
			final long now = System.nanoTime();
			if (current.isEmpty() || ((now - started) < STALL_NS)
					|| ((current == dumpedStep) && ((now - dumpedNs) < STALL_NS))) {
				continue;
			}
			dumpedStep = current;
			dumpedNs = now;
			System.out.println("loading: " + seconds(now - this.startNs) + " still on: " + current
					+ (this.detail.isEmpty() ? "" : " (" + this.detail + ")") + " for " + seconds(now - started));
			System.out.flush();
			System.out.println("loading:   game thread:");
			dumpStack(this.glThread);
			describeWorkers();
		}
	}

	@Override
	public void status(final String newStep, final String newDetail) {
		final long now = System.nanoTime();
		final boolean newOne = !newStep.equals(this.step);
		if (newOne) {
			if (!this.step.isEmpty()) {
				System.out.println("loading: " + seconds(now - this.startNs) + " finished in "
						+ seconds(now - this.stepStartNs) + ": " + this.step
						+ (this.detail.isEmpty() ? "" : " (" + this.detail + ")"));
			}
			if (!newStep.isEmpty()) {
				System.out.println("loading: " + seconds(now - this.startNs) + " " + newStep);
			}
			this.step = newStep;
			this.stepStartNs = now;
			setLoadingBoost(!newStep.isEmpty());
		}
		this.detail = newDetail;
		if (this.active && !this.failed && !newStep.isEmpty() && (Thread.currentThread() == this.glThread)
				&& (newOne || ((now - this.lastFrameNs) >= FRAME_NS))) {
			this.lastFrameNs = now;
			draw(now);
		}
	}

	@Override
	public void done(final String what) {
		final long now = System.nanoTime();
		if (!this.step.isEmpty()) {
			System.out.println("loading: " + seconds(now - this.startNs) + " finished in "
					+ seconds(now - this.stepStartNs) + ": " + this.step
					+ (this.detail.isEmpty() ? "" : " (" + this.detail + ")"));
		}
		System.out.println("loading: " + seconds(now - this.startNs) + " done: " + what);
		setLoadingBoost(false);
		this.step = "";
		this.detail = "";
		if (this.active) {
			this.active = false;
			dispose();
		}
	}

	/**
	 * The load failed (the exception is about to end the game): shows what happened for 20 seconds
	 * (HOME and + and - still quit at once), so a missing folder or a bug is not just the game
	 * closing. The exception is the caller's to rethrow; its trace goes to warsmash.log as before.
	 */
	public void fail(final Throwable error) {
		if (!this.active || this.failed || (Thread.currentThread() != this.glThread)) {
			return;
		}
		Throwable root = error;
		while ((root.getCause() != null) && (root.getCause() != root)) {
			root = root.getCause();
		}
		final String name = root.getClass().getName();
		String line = name.substring(name.lastIndexOf('.') + 1);
		if (root.getMessage() != null) {
			line += ": " + root.getMessage();
		}
		if (line.length() > 90) {
			line = line.substring(0, 87) + "...";
		}
		final String during = this.step.isEmpty() ? "" : "While: " + this.step;
		System.out.println("loading: stopped by " + line);
		final long end = System.nanoTime() + ERROR_SHOWN_NS;
		long now;
		while (!this.failed && ((now = System.nanoTime()) < end)) {
			final long left = (end - now) / 1000000000L + 1;
			drawLines(now, RED, "Warsmash stopped", line, during,
					"Details are in sdmc:/switch/warsmash/warsmash.log  -  closing in " + left + " s");
		}
		this.active = false;
	}

	private void draw(final long now) {
		drawLines(now, GOLD, this.step, this.detail, "", "Warsmash  -  " + clock(now - this.startNs));
	}

	private void drawLines(final long now, final Color titleColor, final String title, final String detail,
			final String extra, final String footer) {
		try {
			if (this.batch == null) {
				create();
			}
			final GL20 gl = Gdx.gl;
			final int width = Gdx.graphics.getWidth();
			final int height = Gdx.graphics.getHeight();
			// gdx's meshes bind their own vertex array object and leave 0 bound: put Warsmash's back.
			this.intQuery.clear();
			gl.glGetIntegerv(GL30.GL_VERTEX_ARRAY_BINDING, this.intQuery);
			final int vertexArray = this.intQuery.get(0);

			gl.glViewport(0, 0, width, height);
			gl.glClearColor(0f, 0f, 0f, 1f);
			gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
			this.batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
			this.batch.begin();
			drawCentered(this.bigFont, titleColor, title, width, height * 0.54f);
			if (!detail.isEmpty()) {
				drawCentered(this.smallFont, PALE, detail, width, height * 0.54f - 70);
			}
			if (!extra.isEmpty()) {
				drawCentered(this.smallFont, DIM, extra, width, height * 0.54f - 120);
			}
			drawCentered(this.smallFont, DIM, footer, width, 120);
			drawCentered(this.smallFont, DIM, "Hold + and - together to quit", width, 74);
			this.batch.end();

			Gdx.gl30.glBindVertexArray(vertexArray);
			present();
		}
		catch (final Throwable t) {
			this.failed = true;
			System.out.println("loading: the loading screen failed, carrying on without it: " + t);
			t.printStackTrace();
		}
	}

	private void drawCentered(final BitmapFont font, final Color color, final String text, final int width,
			final float y) {
		font.setColor(color);
		this.layout.setText(font, text);
		font.draw(this.batch, this.layout, (width - this.layout.width) / 2f, y);
	}

	private void create() {
		this.intQuery = BufferUtils.newIntBuffer(16);
		this.layout = new GlyphLayout();
		this.shader = new ShaderProgram(VERTEX, FRAGMENT);
		if (!this.shader.isCompiled()) {
			throw new IllegalStateException("loading screen shader: " + this.shader.getLog());
		}
		this.batch = new SpriteBatch(200, this.shader);
		final FreeTypeFontGenerator generator = new FreeTypeFontGenerator(new FileHandle(new File(FONT)));
		try {
			final FreeTypeFontParameter parameter = new FreeTypeFontParameter();
			parameter.size = 46;
			parameter.borderWidth = 2;
			parameter.borderColor = Color.BLACK;
			this.bigFont = generator.generateFont(parameter);
			parameter.size = 30;
			parameter.borderWidth = 1;
			this.smallFont = generator.generateFont(parameter);
		}
		finally {
			generator.dispose();
		}
	}

	private void dispose() {
		if (this.batch != null) {
			this.batch.dispose();
			this.batch = null;
		}
		if (this.shader != null) {
			this.shader.dispose();
			this.shader = null;
		}
		if (this.bigFont != null) {
			this.bigFont.dispose();
			this.bigFont = null;
		}
		if (this.smallFont != null) {
			this.smallFont.dispose();
			this.smallFont = null;
		}
	}

	/** "12.3 s" */
	private static String seconds(final long ns) {
		final long tenths = ns / 100000000L;
		return (tenths / 10) + "." + (tenths % 10) + " s";
	}

	/** "1:05" */
	private static String clock(final long ns) {
		final long s = ns / 1000000000L;
		final long rest = s % 60;
		return (s / 60) + ":" + (rest < 10 ? "0" : "") + rest;
	}
}
