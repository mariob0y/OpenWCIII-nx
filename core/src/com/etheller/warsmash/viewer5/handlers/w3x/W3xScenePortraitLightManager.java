package com.etheller.warsmash.viewer5.handlers.w3x;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.SceneLightInstance;
import com.etheller.warsmash.viewer5.SceneLightManager;
import com.etheller.warsmash.viewer5.StaticSceneLightInstance;
import com.etheller.warsmash.viewer5.gl.DataTexture;

public class W3xScenePortraitLightManager implements SceneLightManager, W3xSceneLightManager {
	public final List<SceneLightInstance> lights;
	private FloatBuffer lightDataCopyHeap;
	private final DataTexture unitLightsTexture;
	private int unitLightCount;
	private boolean dirty = true;
	private boolean allStatic = true;

	public W3xScenePortraitLightManager(GL20 gl) {
		this.lights = new ArrayList<>();
		this.unitLightsTexture = new DataTexture(gl, 4, 4, 1);
		this.lightDataCopyHeap = ByteBuffer.allocateDirect(16 * 1 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
	}

	public W3xScenePortraitLightManager(final ModelViewer viewer, final Vector3 lightDirection) {
		this(viewer.gl);
		add(StaticSceneLightInstance.createDefault(lightDirection));
	}

	@Override
	public void add(final SceneLightInstance lightInstance) {
		this.lights.add(lightInstance);
		if (!(lightInstance instanceof StaticSceneLightInstance)) {
			this.allStatic = false;
		}
		this.dirty = true;
	}

	@Override
	public void remove(final SceneLightInstance lightInstance) {
		this.lights.remove(lightInstance);
		this.dirty = true;
	}

	@Override
	public void update() {
		if (!this.dirty && this.allStatic) {
			return;
		}
		// warsmash-nx: a new frame for LightInstance's per-frame cache. Only the world light manager advanced
		// it, so in the menu (no world scene) every model light kept the values of its first frame: the
		// campaign screens' animated lights never moved or changed.
		com.etheller.warsmash.viewer5.handlers.mdx.LightInstance.advanceGeneration();
		final int numberOfLights = this.lights.size() + 1;
		final int bytesNeeded = numberOfLights * 4 * 16;
		if (bytesNeeded > (this.lightDataCopyHeap.capacity() * 4)) {
			this.lightDataCopyHeap = ByteBuffer.allocateDirect(bytesNeeded).order(ByteOrder.nativeOrder())
					.asFloatBuffer();
			this.unitLightsTexture.reserve(4, numberOfLights);
		}

		this.unitLightCount = 0;
		this.lightDataCopyHeap.clear();
		int offset = 0;
		for (final SceneLightInstance light : this.lights) {
			light.bind(offset, this.lightDataCopyHeap);
			offset += 16;
			this.unitLightCount++;
		}
		this.lightDataCopyHeap.limit(offset);
		this.unitLightsTexture.bindAndUpdate(this.lightDataCopyHeap, 4, this.unitLightCount);
		this.dirty = false;
	}

	@Override
	public DataTexture getUnitLightsTexture() {
		return this.unitLightsTexture;
	}

	@Override
	public int getUnitLightCount() {
		return this.unitLightCount;
	}

	@Override
	public DataTexture getTerrainLightsTexture() {
		return null;
	}

	@Override
	public int getTerrainLightCount() {
		return 0;
	}
}
