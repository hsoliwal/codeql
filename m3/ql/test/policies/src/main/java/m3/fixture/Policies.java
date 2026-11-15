package m3.fixture;

class Policies {
  static Object sink;

  String canonicalize(String input, InternDecoy decoy) {
    String first = input.intern();
    String second = ((java.lang.String) input).intern();
    decoy.intern();
    decoy.intern(input);
    String text = "input.intern(); for (;;) new Object();";
    // input.intern(); for (;;) new Object();
    return first + second + text;
  }

  void loops(int n, boolean flag) {
    sink = new Object();
    for (int i = 0; i < n; i++) {
      sink = new Object();
      sink = new int[i];
      java.util.function.Supplier<Object> later = () -> new Object();
      Runnable callback = new Runnable() {
        public void run() {
          sink = new Object();
        }
      };
      class Local {
        void deferred() {
          sink = new Object();
        }
      }
      if (flag) {
        for (int j = 0; j < n; j++) {
          sink = new Object();
        }
      }
    }
    while (flag) {
      sink = new Object();
      break;
    }
    do {
      sink = new Object();
    } while (false);
    for (Object value : new Object[] { new Object() }) {
      sink = new Object();
    }
    for (Object once = new Object(); n > 0; n--) {
      break;
    }
    while (new Object() != null) {
      break;
    }
    for (int i = 0; i < 1; sink = new Object(), i++) {
    }
    Runnable withOwnLoop = () -> {
      for (int k = 0; k < 2; k++) {
        sink = new Object();
      }
    };
    sink = new Object();
  }
}

class InternDecoy {
  String intern() {
    return "same name, unrelated receiver";
  }

  String intern(String input) {
    return input;
  }
}
