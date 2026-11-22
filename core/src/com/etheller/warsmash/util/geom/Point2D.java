package com.etheller.warsmash.util.geom;

/**
 * Stand-in for java.awt.geom.Point2D (core must not depend on java.desktop).
 * Only what the simulation uses, with arithmetic, equals and hashCode copied
 * from the JDK so pathfinding results do not change.
 */
public abstract class Point2D {
	protected Point2D() {
	}

	public abstract double getX();

	public abstract double getY();

	public static double distanceSq(double x1, double y1, final double x2, final double y2) {
		x1 -= x2;
		y1 -= y2;
		return (x1 * x1) + (y1 * y1);
	}

	public static double distance(double x1, double y1, final double x2, final double y2) {
		x1 -= x2;
		y1 -= y2;
		return Math.sqrt((x1 * x1) + (y1 * y1));
	}

	public double distanceSq(double px, double py) {
		px -= getX();
		py -= getY();
		return (px * px) + (py * py);
	}

	public double distanceSq(final Point2D pt) {
		final double px = pt.getX() - this.getX();
		final double py = pt.getY() - this.getY();
		return (px * px) + (py * py);
	}

	public double distance(double px, double py) {
		px -= getX();
		py -= getY();
		return Math.sqrt((px * px) + (py * py));
	}

	public double distance(final Point2D pt) {
		final double px = pt.getX() - this.getX();
		final double py = pt.getY() - this.getY();
		return Math.sqrt((px * px) + (py * py));
	}

	@Override
	public int hashCode() {
		long bits = java.lang.Double.doubleToLongBits(getX());
		bits ^= java.lang.Double.doubleToLongBits(getY()) * 31;
		return (((int) bits) ^ ((int) (bits >> 32)));
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof Point2D) {
			final Point2D p2d = (Point2D) obj;
			return (getX() == p2d.getX()) && (getY() == p2d.getY());
		}
		return super.equals(obj);
	}

	public static class Float extends Point2D {
		public float x;
		public float y;

		public Float() {
		}

		public Float(final float x, final float y) {
			this.x = x;
			this.y = y;
		}

		@Override
		public double getX() {
			return this.x;
		}

		@Override
		public double getY() {
			return this.y;
		}

		public void setLocation(final float x, final float y) {
			this.x = x;
			this.y = y;
		}

		@Override
		public String toString() {
			return "Point2D.Float[" + this.x + ", " + this.y + "]";
		}
	}
}
