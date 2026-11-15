package m3.fixture;

public class Inventory {
  public Inventory() { this(0); }
  protected Inventory(int count) { super(); }
  public int value() { return 0; }
  public int value(int value) { return value; }
  protected String label(String input) { return input; }
  void packageOnly() {}
  private void privateOnly() {}
  public native int nativeRead(byte[] target);
  protected static native void nativeWrite(long address);
  private native void privateNative();

  public void dispatch(Api api) {
    api.run();
    value();
    value(1);
    new Worker().run();
    nativeRead(new byte[0]);
    nativeWrite(0L);
  }

  public static class DefaultConstructed {}

  public interface Api {
    void run();
  }

  public static class Worker implements Api {
    public Worker() { super(); }
    public void run() {}
  }

  // public native void commentDecoy();
  private String text = "public native void stringDecoy();";
}
