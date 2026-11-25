package com.etheller.warsmash.viewer5;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.etheller.warsmash.common.FetchDataTypeName;
import com.etheller.warsmash.common.LoadGenericCallback;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.AssetCacheTelemetry;
import com.etheller.warsmash.viewer5.gl.ClientBuffer;
import com.etheller.warsmash.viewer5.gl.WebGL;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;
import com.etheller.warsmash.viewer5.handlers.ResourceHandlerConstructionParams;

public abstract class ModelViewer {
	public DataSource dataSource;
	public final CanvasProvider canvas;
	public List<Resource> resources;
	public Map<String, Resource> fetchCache;
	public int frameTime;
	public GL20 gl;
	public WebGL webGL;
	public List<Scene> scenes;
	private int visibleCells;
	private int visibleInstances;
	private int updatedParticles;
	public int frame;
	public final int rectBuffer;
	public ClientBuffer buffer;
	public boolean audioEnabled;
	private final Map<Model<?>, List<TextureMapper>> textureMappers;
	private final Set<ResourceHandler> handlers;

	public ModelViewer(final DataSource dataSource, final CanvasProvider canvas) {
		this.dataSource = dataSource;
		this.canvas = canvas;
		this.resources = new ArrayList<>();
		this.fetchCache = new HashMap<>();
		this.handlers = new HashSet<ResourceHandler>();
		this.frameTime = 1000 / 60;
		this.gl = Gdx.gl;
		this.webGL = new WebGL(this.gl);
		this.scenes = new ArrayList<>();
		this.visibleCells = 0;
		this.visibleInstances = 0;
		this.updatedParticles = 0;

		this.frame = 0;

		this.rectBuffer = this.gl.glGenBuffer();
		this.buffer = new ClientBuffer(this.gl);
		this.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, this.rectBuffer);
		final ByteBuffer temp = ByteBuffer.allocateDirect(6).order(ByteOrder.nativeOrder());
		temp.put((byte) 0);
		temp.put((byte) 1);
		temp.put((byte) 2);
		temp.put((byte) 0);
		temp.put((byte) 2);
		temp.put((byte) 3);
		temp.clear();
		this.gl.glBufferData(GL20.GL_ARRAY_BUFFER, temp.capacity(), temp, GL20.GL_STATIC_DRAW);
		this.audioEnabled = false;
		this.textureMappers = new HashMap<Model<?>, List<TextureMapper>>();
	}

	public void setDataSource(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public boolean enableAudio() {
		this.audioEnabled = true;
		return this.audioEnabled;
	}

	public boolean addHandler(ResourceHandler handler) {
		if (handler != null) {

			// Allow to pass also the handler's module for convenience.
			if (handler.handler != null) {
				handler = handler.handler;
			}

			if (!this.handlers.contains(handler)) {
				// Check if the handler has a loader, and if so load it.
				if (handler.load && !handler.load(this)) {
					onResourceLoadError();
					return false;
				}

				this.handlers.add(handler);

				return true;
			}
		}
		return false;
	}

	public Scene addSimpleScene() {
		final Scene scene = new SimpleScene(this, createLightManager(true));

		this.scenes.add(scene);

		return scene;
	}

	public WorldScene addWorldScene() {
		final WorldScene scene = new WorldScene(this, createLightManager(false));

		this.scenes.add(scene);

		return scene;
	}

	public boolean removeScene(final Scene scene) {
		return this.scenes.remove(scene);
	}

	public void clear() {
		this.scenes.clear();
	}

	public Object[] findHandler(final String ext) {
		for (final ResourceHandler handler : this.handlers) {
			for (final String[] extension : handler.extensions) {
				if (extension[0].equals(ext)) {
					return new Object[] { handler, extension[1] };
				}
			}
		}
		return null;
	}

	private static final java.util.Set<String> REPORTED_MISSING = new java.util.HashSet<>();

	private static final class PredecodedTexture {
		final int width;
		final int height;
		final java.nio.ByteBuffer rgba;

		PredecodedTexture(final int width, final int height, final java.nio.ByteBuffer rgba) {
			this.width = width;
			this.height = height;
			this.rgba = rgba;
		}
	}

	private final Map<String, PredecodedTexture> predecodedTextures = new HashMap<>();

	/**
	 * warsmash-nx: reads raw bytes of uncached BLP/DDS textures on the calling thread (safe for
	 * non-thread-safe MPQ streams), then decodes JPEG/BLP/DDS + RGBA packing in parallel across
	 * ParallelJobs workers. Subsequent load() calls consume the decoded buffers directly.
	 */
	public void predecodeTextures(final List<String> paths, final PathSolver pathSolver, final Object solverParams) {
		if ((paths == null) || (paths.size() < 2) || (pathSolver == null)
				|| (com.etheller.warsmash.util.ParallelJobs.workerCount() == 0)) {
			return;
		}
		final List<String> keys = new ArrayList<>(paths.size());
		final List<byte[]> rawFiles = new ArrayList<>(paths.size());
		final java.util.Set<String> queued = new java.util.HashSet<>();
		for (int i = 0, n = paths.size(); i < n; i++) {
			final SolvedPath solved = pathSolver.solve(paths.get(i), solverParams);
			if (!solved.isFetch()) {
				continue;
			}
			String finalSrc = solved.getFinalSrc();
			if (!this.dataSource.has(finalSrc)) {
				final int dot = finalSrc.lastIndexOf('.');
				if (dot >= 0) {
					final String ddsPath = finalSrc.substring(0, dot) + ".dds";
					if (this.dataSource.has(ddsPath)) {
						finalSrc = ddsPath;
					}
					else {
						continue;
					}
				}
				else {
					continue;
				}
			}
			if (this.fetchCache.containsKey(finalSrc) || this.predecodedTextures.containsKey(finalSrc)
					|| !queued.add(finalSrc)) {
				continue;
			}
			String ext = solved.getExtension();
			if (ext == null) {
				continue;
			}
			if (!ext.startsWith(".")) {
				ext = "." + ext;
			}
			final String lowerExt = ext.toLowerCase();
			if (!".blp".equals(lowerExt) && !".dds".equals(lowerExt)) {
				continue;
			}
			try (java.io.InputStream in = this.dataSource.getResourceAsStream(finalSrc)) {
				if (in != null) {
					keys.add(finalSrc);
					rawFiles.add(com.etheller.warsmash.util.image.ImageDecoders.readAll(in));
				}
			}
			catch (final Exception ignored) {
				// Let load() handle error reporting normally.
			}
		}
		final int count = keys.size();
		if (count < 2) {
			return;
		}
		final PredecodedTexture[] results = new PredecodedTexture[count];
		com.etheller.warsmash.util.ParallelJobs.parallelFor(count, (idx, worker) -> {
			try {
				final com.etheller.warsmash.util.image.PixelImage img = com.etheller.warsmash.util.image.ImageDecoders
						.decode(rawFiles.get(idx));
				if (img != null) {
					final java.nio.ByteBuffer buf = com.etheller.warsmash.util.ImageUtils.toRgbaBuffer(img);
					buf.flip();
					results[idx] = new PredecodedTexture(img.getWidth(), img.getHeight(), buf);
				}
			}
			catch (final Exception ignored) {
				// Fall back to normal sequential decode path in load().
			}
		});
		for (int i = 0; i < count; i++) {
			if (results[i] != null) {
				this.predecodedTextures.put(keys.get(i), results[i]);
			}
		}
	}

	/** warsmash-nx: MDX files parsed ahead by {@link #preloadModels}, consumed by the next load() of each */
	private final Map<String, com.hiveworkshop.rms.parsers.mdlx.MdlxModel> preparsedModels = new HashMap<>();

	/**
	 * warsmash-nx: loads many MDX models with their parsing and texture decoding spread over the ParallelJobs
	 * workers, for a loader about to load them one by one (the map's doodads and destructables: one model at a
	 * time, their JPEG textures decoding in sequence, was most of that load step). Per chunk of
	 * {@code chunkSize} models: the files are read here (archive streams are not thread-safe), parsed in
	 * parallel, all their textures decoded in one parallel batch, then each model is load()ed in order on this
	 * thread (GL work), so the later loads are cache hits. Chunks bound how many decoded textures wait in memory
	 * at once. Only binary MDX is parsed ahead; anything else, already loaded or missing is left to load().
	 */
	public void preloadModels(final java.util.Collection<String> paths, final PathSolver pathSolver,
			final Object solverParams, final int chunkSize) {
		if ((paths == null) || paths.isEmpty() || (pathSolver == null)
				|| (com.etheller.warsmash.util.ParallelJobs.workerCount() == 0)) {
			return;
		}
		final List<String> pending = new ArrayList<>(paths.size());
		final java.util.Set<String> seen = new java.util.HashSet<>();
		for (final String path : paths) {
			if ((path == null) || (path.lastIndexOf('.') < 0) || !seen.add(path)) {
				continue;
			}
			final SolvedPath solved = pathSolver.solve(path, solverParams);
			final String finalSrc = solved.getFinalSrc();
			if (!solved.isFetch() || !finalSrc.toLowerCase().endsWith(".mdx")
					|| this.fetchCache.containsKey(finalSrc) || !this.dataSource.has(finalSrc)) {
				continue;
			}
			pending.add(path);
		}
		for (int start = 0; start < pending.size(); start += chunkSize) {
			final List<String> chunk = pending.subList(start, Math.min(pending.size(), start + chunkSize));
			final List<String> keys = new ArrayList<>(chunk.size());
			final List<byte[]> rawFiles = new ArrayList<>(chunk.size());
			for (final String path : chunk) {
				final String finalSrc = pathSolver.solve(path, solverParams).getFinalSrc();
				try (java.io.InputStream in = this.dataSource.getResourceAsStream(finalSrc)) {
					if (in != null) {
						final byte[] bytes = com.etheller.warsmash.util.image.ImageDecoders.readAll(in);
						// binary "MDLX" only: the text MDL parser is not checked for use off the game thread
						if ((bytes.length > 4) && (bytes[0] == 'M') && (bytes[1] == 'D') && (bytes[2] == 'L')
								&& (bytes[3] == 'X')) {
							keys.add(finalSrc);
							rawFiles.add(bytes);
						}
					}
				}
				catch (final Exception ignored) {
					// load() reads it again and reports it
				}
			}
			final int count = keys.size();
			final com.hiveworkshop.rms.parsers.mdlx.MdlxModel[] parsed = new com.hiveworkshop.rms.parsers.mdlx.MdlxModel[count];
			com.etheller.warsmash.util.ParallelJobs.parallelFor(count, (idx, worker) -> {
				try {
					parsed[idx] = new com.hiveworkshop.rms.parsers.mdlx.MdlxModel(
							java.nio.ByteBuffer.wrap(rawFiles.get(idx)));
				}
				catch (final RuntimeException e) {
					// load() parses it again and reports the error
				}
			});
			final List<String> texturePaths = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				if (parsed[i] != null) {
					this.preparsedModels.put(keys.get(i), parsed[i]);
					texturePaths.addAll(com.etheller.warsmash.viewer5.handlers.mdx.MdxModel
							.resolveTexturePaths(parsed[i], this.dataSource));
				}
			}
			predecodeTextures(texturePaths, pathSolver, solverParams);
			for (final String path : chunk) {
				load(path, pathSolver, solverParams);
			}
			// whatever a load did not reach (it failed first) is not kept in memory
			this.preparsedModels.clear();
			this.predecodedTextures.clear();
		}
	}

	public Resource load(final String src, final PathSolver pathSolver, final Object solverParams) {
		String finalSrc = src;
		String extension = "";
		boolean isFetch = false;

		// If a given path solver, resolve.
		if (pathSolver != null) {
			final SolvedPath solved = pathSolver.solve(src, solverParams);

			finalSrc = solved.getFinalSrc();
			if (!this.dataSource.has(finalSrc)) {
				final String ddsPath = finalSrc.substring(0, finalSrc.lastIndexOf('.')) + ".dds";
				if (this.dataSource.has(ddsPath)) {
					finalSrc = ddsPath;
				}
				else if (REPORTED_MISSING.add(finalSrc)) {
					// warsmash-nx: once per file (OrcX01 asked for "_.mdx" 3,167 times).
					System.err.println("Attempting to load non-existant file: " + finalSrc);
				}
			}
			extension = solved.getExtension();
			isFetch = solved.isFetch();

			if (!(extension instanceof String)) {
				throw new IllegalStateException("The path solver did not return an extension!");
			}

			if (extension.charAt(0) != '.') {
				extension = '.' + extension;
			}
			// Built-in texture sources
			// ---- TODO not using JS code here

			final Object[] handlerAndDataType = this.findHandler(extension.toLowerCase());

			// Is there a handler for this file type?
			if (handlerAndDataType != null) {
				if (isFetch) {
					final Resource resource = this.fetchCache.get(finalSrc);

					if (resource != null) {
						AssetCacheTelemetry.recordHit(finalSrc);
						return resource;
					}
					AssetCacheTelemetry.recordMiss(finalSrc);
				}

				final ResourceHandler handler = (ResourceHandler) handlerAndDataType[0];
				final Resource resource = handler.construct(new ResourceHandlerConstructionParams(this, handler,
						extension, pathSolver, isFetch ? finalSrc : ""));

				this.resources.add(resource);

//				if (isFetch) {
				this.fetchCache.put(finalSrc, resource);
//				}

				final PredecodedTexture predecoded = this.predecodedTextures.remove(finalSrc);
				if ((predecoded != null) && (resource instanceof RawOpenGLTextureResource)) {
					final boolean sRGBFix = resource instanceof com.etheller.warsmash.viewer5.handlers.blp.BlpTexture;
					((RawOpenGLTextureResource) resource).updatePredecoded(predecoded.width, predecoded.height,
							predecoded.rgba, sRGBFix);
					resource.loaded = true;
					resource.ok = true;
					resource.lateLoad();
					return resource;
				}

				final com.hiveworkshop.rms.parsers.mdlx.MdlxModel preparsed = this.preparsedModels.isEmpty() ? null
						: this.preparsedModels.remove(finalSrc);
				if ((preparsed != null)
						&& (resource instanceof com.etheller.warsmash.viewer5.handlers.mdx.MdxModel)) {
					// the same steps as Resource.loadData, from the already parsed file
					resource.loaded = true;
					try {
						((com.etheller.warsmash.viewer5.handlers.mdx.MdxModel) resource).load(preparsed);
						resource.ok = true;
						resource.lateLoad();
					}
					catch (final Exception e) {
						resource.error(e);
					}
					return resource;
				}

				// TODO this is a synchronous hack, skipped some Ghostwolf code
				try {
					final java.io.InputStream stream = this.dataSource.getResourceAsStream(finalSrc);
					if (stream == null) {
						resource.loaded = true;
						resource.ok = false;
						return resource;
					}
					resource.loadData(stream, null);
				}
				catch (final Exception e) {
					throw new IllegalStateException("Unable to load data: " + finalSrc, e);
				}

				return resource;
			}
			else {
				throw new IllegalStateException("Missing handler for: " + finalSrc);
			}
		}
		else {
			throw new IllegalStateException(
					"Could not resolve " + finalSrc + ". Did you forget to pass a path solver?");
		}

	}

	public boolean has(final String key) {
		return this.fetchCache.containsKey(key);
	}

	public Resource get(final String key) {
		return this.fetchCache.get(key);
	}

	public GenericResource loadGeneric(final String path, final FetchDataTypeName dataType,
			final LoadGenericCallback callback) {
		return loadGeneric(path, dataType, callback, this.dataSource);
	}

	/**
	 * Load something generic.
	 *
	 * Unlike load(), this does not use handlers or construct any internal objects.
	 *
	 * `dataType` can be one of: `"image"`, `"string"`, `"arrayBuffer"`, `"blob"`.
	 *
	 * If `callback` isn't given, the resource's `data` is the fetch data, according
	 * to `dataType`.
	 *
	 * If `callback` is given, the resource's `data` is the value returned by it
	 * when called with the fetch data.
	 *
	 * If `callback` returns a promise, the resource's `data` will be whatever the
	 * promise resolved to.
	 */
	public GenericResource loadGeneric(final String path, final FetchDataTypeName dataType,
			final LoadGenericCallback callback, final DataSource dataSource) {
		final Resource cachedResource = this.fetchCache.get(path);

		if (cachedResource != null) {
			// Technically also non-generic resources can be returned here, since the fetch
			// cache is shared.
			// That being said, this should be used for generic resources, and it makes the
			// typing a lot easier.
			AssetCacheTelemetry.recordHit(path);
			return (GenericResource) cachedResource;
		}
		AssetCacheTelemetry.recordMiss(path);

		final GenericResource resource = new GenericResource(this, null, null, path, callback);

		this.resources.add(resource);
		this.fetchCache.put(path, resource);

		// TODO this is a synchronous hack, skipped some Ghostwolf code
		try {
			resource.loadData(dataSource.getResourceAsStream(path), null);
		}
		catch (final IOException e) {
			throw new IllegalStateException("Unable to load data: " + path);
		}

		return resource;

	}

	public void updateAndRender() {
		com.etheller.warsmash.util.NxCrumbs.crumb("viewer: sound settings");
		com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFilename.refreshSettings();
		final long t0 = System.nanoTime();
		this.update();
		final long t1 = System.nanoTime();
		com.etheller.warsmash.util.NxCrumbs.crumb("viewer: start frame");
		this.startFrame();
		this.render();
		this.lastUpdateNanos = t1 - t0;
		this.lastRenderNanos = System.nanoTime() - t1;
	}

	/** warsmash-nx: the CPU time of the last updateAndRender(), for the menu's [NxMenu] log line. */
	public long lastUpdateNanos;
	public long lastRenderNanos;

//	public Resource loadGeneric(String path, String dataType, )

	public boolean unload(final Resource resource) {
		// TODO Auto-generated method stub
		final String fetchUrl = resource.fetchUrl;
		if (!"".equals(fetchUrl)) {
			this.fetchCache.remove(fetchUrl);
		}
		return this.resources.remove(resource);
	}

	/**
	 * warsmash-nx: the longest step one frame may animate by. The menu sets it: its first frame's delta
	 * is the whole load before it, which played the glue panels' slide-in out before it was seen.
	 */
	public float maxFrameDelta = Float.MAX_VALUE;

	public void update() {
		final float dt = Math.min(Gdx.graphics.getDeltaTime(), this.maxFrameDelta);

		this.frame += 1;

		this.visibleCells = 0;
		this.visibleInstances = 0;
		this.updatedParticles = 0;

		int sceneIndex = 0;
		for (final Scene scene : this.scenes) {
			if (com.etheller.warsmash.util.NxCrumbs.on()) {
				com.etheller.warsmash.util.NxCrumbs.crumb("viewer: scene " + sceneIndex + " update, dt " + dt
						+ ", " + scene.instances.size() + " instances");
			}
			scene.update(dt);
			if (com.etheller.warsmash.util.NxCrumbs.on()) {
				com.etheller.warsmash.util.NxCrumbs.crumb("viewer: scene " + sceneIndex + " updated, "
						+ scene.updatedParticles + " particles");
			}
			sceneIndex++;

			this.visibleCells += scene.visibleCells;
			this.visibleInstances += scene.visibleInstances;
			this.updatedParticles += scene.updatedParticles;
		}
	}

	public void startFrame() {
		Gdx.gl.glScissor(0, 0, (int) this.canvas.getWidth(), (int) this.canvas.getHeight());
		this.gl.glDepthMask(true);
		this.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
//		WarsmashGdxGame.bindDefaultVertexArray();
	}

	public void render() {
		int sceneIndex = 0;
		for (final Scene scene : this.scenes) {
			final boolean crumbs = com.etheller.warsmash.util.NxCrumbs.on();
			if (crumbs) {
				com.etheller.warsmash.util.NxCrumbs.crumb("viewer: scene " + sceneIndex + " start frame");
			}
			scene.startFrame();
			if (crumbs) {
				com.etheller.warsmash.util.NxCrumbs.crumb("viewer: scene " + sceneIndex + " opaque");
			}
			scene.renderOpaque();
			if (crumbs) {
				com.etheller.warsmash.util.NxCrumbs.crumb("viewer: scene " + sceneIndex + " translucent");
			}
			scene.renderTranslucent();
			sceneIndex++;
		}
	}

	public TextureMapper baseTextureMapper(final ModelInstance instance) {
		final Model<?> model = instance.model;
		List<TextureMapper> mappers = this.textureMappers.get(model);
		if (mappers == null) {
			mappers = new ArrayList<>();
			this.textureMappers.put(model, mappers);
		}
		if (mappers.isEmpty()) {
			mappers.add(new TextureMapper(model));
		}
		return mappers.get(0);
	}

	public TextureMapper changeTextureMapper(final ModelInstance instance, final Object key, final Texture texture) {
		final Map<Object, Texture> map = new HashMap<>(instance.textureMapper.textures);

		if (texture instanceof Texture) { // not null?
			map.put(key, texture);
		}
		else {
			map.remove(key);
		}

		final Model<?> model = instance.model;
		final List<TextureMapper> mappers = this.textureMappers.get(model);

		for (final TextureMapper mapper : mappers) {
			if (mapper.textures.equals(map)) {
				return mapper;
			}
		}

		final TextureMapper mapper = new TextureMapper(model, map);

		mappers.add(mapper);

		return mapper;
	}

	private void onResourceLoadError() {
		System.err.println("error, this, InvalidHandler, FailedToLoad");
	}

	public abstract SceneLightManager createLightManager(boolean simple);
}
