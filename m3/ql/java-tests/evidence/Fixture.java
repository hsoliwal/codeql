package m3fixture;

public class Fixture {
    private int field;

    public Fixture() { this.field = 1; }

    public int arithmetic(int value) { return value + 1; }
    public int overload(int value) { return arithmetic(value); }
    public long overload(long value) { return value + 1L; }

    public void set(int value) { field = value; }
    public void compound(int value) { field += value; }
    public int increment() { return ++field; }
    public int read() { return field; }

    public void setArray(int[] values) { values[0] = 1; }
    public void compoundArray(int[] values) { values[0] += 2; }
    public int preIncrementArray(int[] values) { return ++values[0]; }
    public int postDecrementArray(int[] values) { return values[0]--; }

    public synchronized int synchronizedMethod() { return field; }
    public void synchronizedBlock(Object lock) { synchronized (lock) { field++; } }
    public native int nativeMethod();
    public void throwing() throws IllegalStateException { throw new IllegalStateException(); }

    public String fakeEvidence() {
        // field++; nativeMethod(); throw new IllegalStateException();
        return "field++; nativeMethod(); throw new IllegalStateException();";
    }

    public Runnable lambda() { return () -> set(2); }

    public static class Nested {
        public int call(Fixture fixture) { return fixture.overload(3); }
    }
}
