package java.util.concurrent;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * warsmash-nx: missing from Clearwing 3.1.2 (Gson constructs one for fields typed
 * ConcurrentNavigableMap/ConcurrentSkipListMap; Warsmash itself never does). Not a skip list: a
 * TreeMap behind one lock, and every sub-map view shares that lock. Same ordering, nulls refused,
 * entries handed out are immutable snapshots, as the JDK's. Deviation: the key/entry/value views
 * and their iterators are the TreeMap's, so they are not weakly consistent (a concurrent change may
 * throw ConcurrentModificationException).
 */
public class ConcurrentSkipListMap<K, V> extends AbstractMap<K, V> implements ConcurrentNavigableMap<K, V>, Cloneable {
	private final NavigableMap<K, V> m;
	private final Object lock;

	public ConcurrentSkipListMap() {
		this(new TreeMap<K, V>(), null);
	}

	public ConcurrentSkipListMap(Comparator<? super K> comparator) {
		this(new TreeMap<K, V>(comparator), null);
	}

	public ConcurrentSkipListMap(Map<? extends K, ? extends V> map) {
		this(new TreeMap<K, V>(), null);
		putAll(map);
	}

	public ConcurrentSkipListMap(SortedMap<K, ? extends V> map) {
		this(new TreeMap<K, V>(map.comparator()), null);
		putAll(map);
	}

	private ConcurrentSkipListMap(NavigableMap<K, V> m, Object lock) {
		this.m = m;
		this.lock = lock != null ? lock : this;
	}

	private ConcurrentSkipListMap<K, V> view(NavigableMap<K, V> v) {
		return new ConcurrentSkipListMap<K, V>(v, lock);
	}

	private static void checkKey(Object key) {
		if (key == null)
			throw new NullPointerException();
	}

	private static void checkKeyValue(Object key, Object value) {
		if (key == null || value == null)
			throw new NullPointerException();
	}

	/** As the JDK: entries handed out are immutable snapshots. */
	private static <K, V> Map.Entry<K, V> snapshot(Map.Entry<K, V> e) {
		return e == null ? null : new AbstractMap.SimpleImmutableEntry<K, V>(e.getKey(), e.getValue());
	}

	@Override
	public int size() {
		synchronized (lock) {
			return m.size();
		}
	}

	@Override
	public boolean isEmpty() {
		synchronized (lock) {
			return m.isEmpty();
		}
	}

	@Override
	public boolean containsKey(Object key) {
		checkKey(key);
		synchronized (lock) {
			return m.containsKey(key);
		}
	}

	@Override
	public boolean containsValue(Object value) {
		if (value == null)
			throw new NullPointerException();
		synchronized (lock) {
			return m.containsValue(value);
		}
	}

	@Override
	public V get(Object key) {
		checkKey(key);
		synchronized (lock) {
			return m.get(key);
		}
	}

	@Override
	public V put(K key, V value) {
		checkKeyValue(key, value);
		synchronized (lock) {
			return m.put(key, value);
		}
	}

	@Override
	public V remove(Object key) {
		checkKey(key);
		synchronized (lock) {
			return m.remove(key);
		}
	}

	@Override
	public void clear() {
		synchronized (lock) {
			m.clear();
		}
	}

	@Override
	public V putIfAbsent(K key, V value) {
		checkKeyValue(key, value);
		synchronized (lock) {
			V old = m.get(key);
			if (old == null)
				m.put(key, value);
			return old;
		}
	}

	@Override
	public boolean remove(Object key, Object value) {
		checkKey(key);
		synchronized (lock) {
			V old = m.get(key);
			if (old != null && value != null && old.equals(value)) {
				m.remove(key);
				return true;
			}
			return false;
		}
	}

	@Override
	public V replace(K key, V value) {
		checkKeyValue(key, value);
		synchronized (lock) {
			return m.containsKey(key) ? m.put(key, value) : null;
		}
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		checkKeyValue(key, oldValue);
		if (newValue == null)
			throw new NullPointerException();
		synchronized (lock) {
			V old = m.get(key);
			if (old != null && old.equals(oldValue)) {
				m.put(key, newValue);
				return true;
			}
			return false;
		}
	}

	@Override
	public Comparator<? super K> comparator() {
		return m.comparator();
	}

	@Override
	public K firstKey() {
		synchronized (lock) {
			return m.firstKey();
		}
	}

	@Override
	public K lastKey() {
		synchronized (lock) {
			return m.lastKey();
		}
	}

	@Override
	public Map.Entry<K, V> firstEntry() {
		synchronized (lock) {
			return snapshot(m.firstEntry());
		}
	}

	@Override
	public Map.Entry<K, V> lastEntry() {
		synchronized (lock) {
			return snapshot(m.lastEntry());
		}
	}

	@Override
	public Map.Entry<K, V> pollFirstEntry() {
		synchronized (lock) {
			return snapshot(m.pollFirstEntry());
		}
	}

	@Override
	public Map.Entry<K, V> pollLastEntry() {
		synchronized (lock) {
			return snapshot(m.pollLastEntry());
		}
	}

	@Override
	public Map.Entry<K, V> lowerEntry(K key) {
		checkKey(key);
		synchronized (lock) {
			return snapshot(m.lowerEntry(key));
		}
	}

	@Override
	public K lowerKey(K key) {
		checkKey(key);
		synchronized (lock) {
			return m.lowerKey(key);
		}
	}

	@Override
	public Map.Entry<K, V> floorEntry(K key) {
		checkKey(key);
		synchronized (lock) {
			return snapshot(m.floorEntry(key));
		}
	}

	@Override
	public K floorKey(K key) {
		checkKey(key);
		synchronized (lock) {
			return m.floorKey(key);
		}
	}

	@Override
	public Map.Entry<K, V> ceilingEntry(K key) {
		checkKey(key);
		synchronized (lock) {
			return snapshot(m.ceilingEntry(key));
		}
	}

	@Override
	public K ceilingKey(K key) {
		checkKey(key);
		synchronized (lock) {
			return m.ceilingKey(key);
		}
	}

	@Override
	public Map.Entry<K, V> higherEntry(K key) {
		checkKey(key);
		synchronized (lock) {
			return snapshot(m.higherEntry(key));
		}
	}

	@Override
	public K higherKey(K key) {
		checkKey(key);
		synchronized (lock) {
			return m.higherKey(key);
		}
	}

	@Override
	public ConcurrentNavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		checkKeyValue(fromKey, toKey);
		synchronized (lock) {
			return view(m.subMap(fromKey, fromInclusive, toKey, toInclusive));
		}
	}

	@Override
	public ConcurrentNavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		checkKey(toKey);
		synchronized (lock) {
			return view(m.headMap(toKey, inclusive));
		}
	}

	@Override
	public ConcurrentNavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		checkKey(fromKey);
		synchronized (lock) {
			return view(m.tailMap(fromKey, inclusive));
		}
	}

	@Override
	public ConcurrentNavigableMap<K, V> subMap(K fromKey, K toKey) {
		return subMap(fromKey, true, toKey, false);
	}

	@Override
	public ConcurrentNavigableMap<K, V> headMap(K toKey) {
		return headMap(toKey, false);
	}

	@Override
	public ConcurrentNavigableMap<K, V> tailMap(K fromKey) {
		return tailMap(fromKey, true);
	}

	@Override
	public ConcurrentNavigableMap<K, V> descendingMap() {
		synchronized (lock) {
			return view(m.descendingMap());
		}
	}

	@Override
	public NavigableSet<K> navigableKeySet() {
		return m.navigableKeySet();
	}

	@Override
	public NavigableSet<K> keySet() {
		return m.navigableKeySet();
	}

	@Override
	public NavigableSet<K> descendingKeySet() {
		return m.descendingKeySet();
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return m.entrySet();
	}

	@Override
	public Collection<V> values() {
		return m.values();
	}

	@Override
	public ConcurrentSkipListMap<K, V> clone() {
		synchronized (lock) {
			ConcurrentSkipListMap<K, V> copy = new ConcurrentSkipListMap<K, V>(comparator());
			copy.putAll(m);
			return copy;
		}
	}
}
