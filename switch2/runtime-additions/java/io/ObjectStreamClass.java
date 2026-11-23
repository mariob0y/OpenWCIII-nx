package java.io;

/**
 * warsmash-nx: the class only, for the class literal in Gson's UnsafeAllocator (it then asks for the
 * private method getConstructorId by reflection, gets NoSuchMethodException, and moves on to its
 * next strategy). Java serialization is not supported by this runtime.
 */
public class ObjectStreamClass implements Serializable {
    private ObjectStreamClass() {
    }
}
