package java.util.concurrent.atomic;

import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

/**
 * warsmash-nx: missing from Clearwing 3.1.2. A long[] behind the object's monitor, as Clearwing's
 * own AtomicIntegerArray (Java 8 API; results and toString as the JDK's).
 */
public class AtomicLongArray implements java.io.Serializable {
	private static final long serialVersionUID = -2308431214976778248L;
	private final long[] array;

	public AtomicLongArray(int length) {
		array = new long[length];
	}

	public AtomicLongArray(long[] array) {
		this.array = array.clone();
	}

	public final int length() {
		return array.length;
	}

	public final synchronized long get(int i) {
		return array[i];
	}

	public final synchronized void set(int i, long newValue) {
		array[i] = newValue;
	}

	public final void lazySet(int i, long newValue) {
		set(i, newValue);
	}

	public final synchronized long getAndSet(int i, long newValue) {
		long value = array[i];
		array[i] = newValue;
		return value;
	}

	public final synchronized boolean compareAndSet(int i, long expect, long update) {
		if (array[i] != expect)
			return false;
		array[i] = update;
		return true;
	}

	public final boolean weakCompareAndSet(int i, long expect, long update) {
		return compareAndSet(i, expect, update);
	}

	public final synchronized long getAndAdd(int i, long delta) {
		long value = array[i];
		array[i] = value + delta;
		return value;
	}

	public final long getAndIncrement(int i) {
		return getAndAdd(i, 1);
	}

	public final long getAndDecrement(int i) {
		return getAndAdd(i, -1);
	}

	public final long incrementAndGet(int i) {
		return getAndAdd(i, 1) + 1;
	}

	public final long decrementAndGet(int i) {
		return getAndAdd(i, -1) - 1;
	}

	public final long addAndGet(int i, long delta) {
		return getAndAdd(i, delta) + delta;
	}

	public final synchronized long getAndUpdate(int i, LongUnaryOperator updateFunction) {
		long prev = array[i];
		array[i] = updateFunction.applyAsLong(prev);
		return prev;
	}

	public final synchronized long updateAndGet(int i, LongUnaryOperator updateFunction) {
		return array[i] = updateFunction.applyAsLong(array[i]);
	}

	public final synchronized long getAndAccumulate(int i, long x, LongBinaryOperator accumulatorFunction) {
		long prev = array[i];
		array[i] = accumulatorFunction.applyAsLong(prev, x);
		return prev;
	}

	public final synchronized long accumulateAndGet(int i, long x, LongBinaryOperator accumulatorFunction) {
		return array[i] = accumulatorFunction.applyAsLong(array[i], x);
	}

	@Override
	public synchronized String toString() {
		int iMax = array.length - 1;
		if (iMax == -1)
			return "[]";
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(array[i]);
			if (i == iMax)
				return b.append(']').toString();
			b.append(',').append(' ');
		}
	}
}
