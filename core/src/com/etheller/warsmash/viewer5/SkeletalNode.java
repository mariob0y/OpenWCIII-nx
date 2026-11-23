package com.etheller.warsmash.viewer5;

import java.util.ArrayList;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.util.RenderMathUtils;

public abstract class SkeletalNode extends GenericNode {

	public UpdatableObject object;
	/**
	 * Whether the generic object's own visibility track was on at the last pose evaluation; the
	 * object is updated after the pose (MdxComplexInstance.finishNodes), maybe evaluated on a worker.
	 */
	public boolean objectVisible;

	public boolean billboarded;
	public boolean billboardedX;
	public boolean billboardedY;
	public boolean billboardedZ;

	public Vector3 localBlendLocation;
	public Quaternion localBlendRotation;
	public Vector3 localBlendScale;

	public Quaternion overrideWorldRotation;
	public Quaternion preOverrideLocalRotation;

	public SkeletalNode() {
		this.pivot = new Vector3();
		this.localLocation = new Vector3();
		this.localRotation = new Quaternion(0, 0, 0, 1);
		this.localScale = new Vector3(1, 1, 1);
		this.worldLocation = new Vector3();
		this.worldRotation = new Quaternion();
		this.worldScale = new Vector3(1, 1, 1);
		this.inverseWorldLocation = new Vector3();
		this.inverseWorldRotation = new Quaternion();
		this.inverseWorldScale = new Vector3();
		this.localMatrix = new Matrix4();
		this.localBlendLocation = new Vector3();
		this.localBlendRotation = new Quaternion(0, 0, 0, 1);
		this.localBlendScale = new Vector3(1, 1, 1);
		this.worldMatrix = new Matrix4();
		this.dontInheritTranslation = false;
		this.dontInheritRotation = false;
		this.dontInheritScaling = false;
		this.children = new ArrayList<>();

		this.visible = true;
		this.wasDirty = false;

		/**
		 * The object associated with this node, if there is any.
		 *
		 * @member {?}
		 */
		this.object = null;

		this.localRotation.w = 1;

		this.localScale.set(1, 1, 1);

		this.localMatrix.val[0] = 1;
		this.localMatrix.val[5] = 1;
		this.localMatrix.val[10] = 1;
		this.localMatrix.val[15] = 1;

		this.dirty = true;

		this.billboarded = false;
		this.billboardedX = false;
		this.billboardedY = false;
		this.billboardedZ = false;
	}

	public void recalculateTransformation(final Scene scene, final float blendTimeRatio) {
		recalculateTransformation(scene, blendTimeRatio, PoseScratch.MAIN);
	}

	/**
	 * As {@link #recalculateTransformation(Scene, float)}, with the calling thread's temporaries:
	 * poses are evaluated on several threads at once (WorldScene's parallel pose pass).
	 */
	public void recalculateTransformation(final Scene scene, final float blendTimeRatio, final PoseScratch heap) {
		final float inverseBlendRatio = 1 - blendTimeRatio;
		final Quaternion computedRotation;
		Vector3 computedScaling;
		Vector3 computedLocation;

		if (this.dontInheritScaling) {
			computedScaling = heap.scaling;

			final Vector3 parentInverseScale = this.parent.inverseWorldScale;
			computedScaling.x = parentInverseScale.x * this.localScale.x;
			computedScaling.y = parentInverseScale.y * this.localScale.y;
			computedScaling.z = parentInverseScale.z * this.localScale.z;

			this.worldScale.x = this.localScale.x;
			this.worldScale.y = this.localScale.y;
			this.worldScale.z = this.localScale.z;
		}
		else {
			if (!Float.isNaN(blendTimeRatio) && (blendTimeRatio > 0)) {
				heap.blendScale.set(this.localScale).scl(inverseBlendRatio)
						.add(heap.blend.set(this.localBlendScale).scl(blendTimeRatio));
				computedScaling = heap.blendScale;
			}
			else {
				computedScaling = this.localScale;
			}

			final Vector3 parentScale = this.parent.worldScale;
			this.worldScale.x = parentScale.x * this.localScale.x;
			this.worldScale.y = parentScale.y * this.localScale.y;
			this.worldScale.z = parentScale.z * this.localScale.z;
		}

		if (this.billboarded) {
			computedRotation = heap.rotationA;

			computedRotation.set(this.parent.inverseWorldRotation);
			if (scene != null) {
				// TODO null scene is stupid, and happens rarely
				computedRotation.mul(scene.camera.inverseRotation);
			}

			convertBasis(computedRotation);
			computedRotation.mul(this.localRotation);
		}
		else {
			computedRotation = heap.rotationA.set(this.localRotation);
			if (!Float.isNaN(blendTimeRatio) && (blendTimeRatio > 0)) {
				computedRotation.slerp(this.localBlendRotation, blendTimeRatio);
			}

			if (this.billboardedX) {
				if (computedScaling == this.localScale) {
					computedScaling = heap.scaling.set(computedScaling);
				}
				// It took me many hours to deduce from playing around that this negative one
				// multiplier should be here. I suggest a lot of testing before you remove it.
				computedScaling.z *= -1;

				final Camera camera = scene.camera;
				heap.cameraRay.set(camera.billboardedVectors[6]);

				heap.rotationB.set(computedRotation);
				// Inverse that local rotation
				heap.rotationB.x = -heap.rotationB.x;
				heap.rotationB.y = -heap.rotationB.y;
				heap.rotationB.z = -heap.rotationB.z;

				heap.rotationB.mul(this.parent.inverseWorldRotation);

				heap.transform(heap.rotationB, heap.cameraRay);

				heap.billboardAxis.set(1, 0, 0);
				final float angle = (float) Math.atan2(heap.cameraRay.z, heap.cameraRay.y);
				heap.rotationB.setFromAxisRad(heap.billboardAxis, angle);

				computedRotation.mul(heap.rotationB);
			}
			else if (this.billboardedY) {
				final Camera camera = scene.camera;
				heap.cameraRay.set(camera.billboardedVectors[6]);

				heap.rotationB.set(computedRotation);
				// Inverse that local rotation
				heap.rotationB.x = -heap.rotationB.x;
				heap.rotationB.y = -heap.rotationB.y;
				heap.rotationB.z = -heap.rotationB.z;

				heap.rotationB.mul(this.parent.inverseWorldRotation);

				heap.transform(heap.rotationB, heap.cameraRay);

				heap.billboardAxis.set(0, 1, 0);
				final float angle = (float) Math.atan2(-heap.cameraRay.z, heap.cameraRay.x);
				heap.rotationB.setFromAxisRad(heap.billboardAxis, angle);

				computedRotation.mul(heap.rotationB);
			}
			else if (this.billboardedZ) {
				final Camera camera = scene.camera;
				heap.cameraRay.set(camera.billboardedVectors[6]);

				heap.rotationB.set(computedRotation);
				// Inverse that local rotation
				heap.rotationB.x = -heap.rotationB.x;
				heap.rotationB.y = -heap.rotationB.y;
				heap.rotationB.z = -heap.rotationB.z;

				heap.rotationB.mul(this.parent.inverseWorldRotation);

				heap.transform(heap.rotationB, heap.cameraRay);

				heap.billboardAxis.set(0, 0, 1);
				final float angle = (float) Math.atan2(heap.cameraRay.y, heap.cameraRay.x);
				heap.rotationB.setFromAxisRad(heap.billboardAxis, angle);

				computedRotation.mul(heap.rotationB);
			}
			else if (this.dontInheritRotation) {
				computedRotation.mulLeft(this.parent.inverseWorldRotation);
			}
		}

		if (!Float.isNaN(blendTimeRatio) && (blendTimeRatio > 0)) {
			computedLocation = heap.blendLocation.set(this.localLocation).scl(inverseBlendRatio)
					.add(heap.blend.set(this.localBlendLocation).scl(blendTimeRatio));
		}
		else {
			computedLocation = this.localLocation;
		}
		RenderMathUtils.fromRotationTranslationScaleOrigin(computedRotation, computedLocation, computedScaling,
				this.localMatrix, this.pivot);

		RenderMathUtils.mul(this.worldMatrix, this.parent.worldMatrix, this.localMatrix);

		RenderMathUtils.mul(this.worldRotation, this.parent.worldRotation, computedRotation);

		// Inverse world rotation
		this.inverseWorldRotation.x = -this.worldRotation.x;
		this.inverseWorldRotation.y = -this.worldRotation.y;
		this.inverseWorldRotation.z = -this.worldRotation.z;
		this.inverseWorldRotation.w = this.worldRotation.w;

		// Inverse world scale
		this.inverseWorldScale.x = 1 / this.worldScale.x;
		this.inverseWorldScale.y = 1 / this.worldScale.y;
		this.inverseWorldScale.z = 1 / this.worldScale.z;

		// World location
		final float x = this.pivot.x;
		final float y = this.pivot.y;
		final float z = this.pivot.z;
		this.worldLocation.x = (this.worldMatrix.val[Matrix4.M00] * x) + (this.worldMatrix.val[Matrix4.M01] * y)
				+ (this.worldMatrix.val[Matrix4.M02] * z) + this.worldMatrix.val[Matrix4.M03];
		this.worldLocation.y = (this.worldMatrix.val[Matrix4.M10] * x) + (this.worldMatrix.val[Matrix4.M11] * y)
				+ (this.worldMatrix.val[Matrix4.M12] * z) + this.worldMatrix.val[Matrix4.M13];
		this.worldLocation.z = (this.worldMatrix.val[Matrix4.M20] * x) + (this.worldMatrix.val[Matrix4.M21] * y)
				+ (this.worldMatrix.val[Matrix4.M22] * z) + this.worldMatrix.val[Matrix4.M23];

		// Inverse world location
		this.inverseWorldLocation.x = -this.worldLocation.x;
		this.inverseWorldLocation.y = -this.worldLocation.y;
		this.inverseWorldLocation.z = -this.worldLocation.z;
	}

	public void beginBlending() {
		this.localBlendLocation.set(this.localLocation);
		if (this.preOverrideLocalRotation != null) {
			this.localBlendRotation.set(this.preOverrideLocalRotation);
			this.preOverrideLocalRotation = null;
		}
		else {
			this.localBlendRotation.set(this.localRotation);
		}
		this.localBlendScale.set(this.localScale);
	}

	public void updateChildren(final float dt, final Scene scene) {
		for (int i = 0, l = this.children.size(); i < l; i++) {
			this.children.get(i).update(dt, scene);
		}
	}

	public void setOverrideWorldRotation(final Quaternion overrideLocalRotation) {
		this.overrideWorldRotation = overrideLocalRotation;
		this.preOverrideLocalRotation = new Quaternion();
		this.preOverrideLocalRotation.set(this.localRotation);
	}

	protected abstract void convertBasis(Quaternion computedRotation);
}
