import M3Evidence

private M3Callable fixtureMethod(string name) {
  result.getDeclaringType().hasQualifiedName("m3fixture", "Fixture") and
  result.hasName(name)
}

private predicate failure(string message) {
  message = "Overloads must have distinct signatures." and
  count(string signature | signature = fixtureMethod("overload").getSignature()) != 2
  or
  message = "Constructor must be inventoried." and
  not exists(M3Callable callable |
    callable.getDeclaringType().hasQualifiedName("m3fixture", "Fixture") and
    callable instanceof Constructor and
    inventoryEvidence(callable, "callable", "()V")
  )
  or
  exists(string method |
    method in ["set", "compound", "increment"] and
    message = "Missing field write: " + method and
    not effectEvidence(fixtureMethod(method), "effect/write-field", "m3fixture.Fixture#field")
  )
  or
  exists(string method |
    method in ["setArray", "compoundArray", "preIncrementArray", "postDecrementArray"] and
    message = "Missing array write: " + method and
    not effectEvidence(fixtureMethod(method), "effect/write-array", "[I")
  )
  or
  message = "Read must be recorded." and
  not effectEvidence(fixtureMethod("read"), "effect/read-field", "m3fixture.Fixture#field")
  or
  message = "Synchronized method must be recorded." and
  not effectEvidence(fixtureMethod("synchronizedMethod"), "effect/monitor", "method")
  or
  message = "Synchronized block must be recorded." and
  not effectEvidence(fixtureMethod("synchronizedBlock"), "effect/monitor", "block")
  or
  message = "Native declaration must be recorded." and
  not effectEvidence(fixtureMethod("nativeMethod"), "effect/native", _)
  or
  message = "Explicit throw must be recorded." and
  not effectEvidence(fixtureMethod("throwing"), "effect/throw", "Ljava/lang/IllegalStateException;")
  or
  message = "Declared throws must be recorded." and
  not inventoryEvidence(fixtureMethod("throwing"), "contract/throws",
    "java.lang.IllegalStateException")
  or
  message = "Declared call must preserve overload." and
  not callEvidence(fixtureMethod("overload"), "call/static", "m3fixture.Fixture#arithmetic(int)")
  or
  message = "Comments and string literals must not create effects." and
  effectEvidence(fixtureMethod("fakeEvidence"), _, _)
  or
  message = "Comments and string literals must not create call edges." and
  callEvidence(fixtureMethod("fakeEvidence"), _, _)
  or
  message = "Simple arithmetic must not create listed local effects." and
  effectEvidence(fixtureMethod("arithmetic"), _, _)
  or
  message = "Java source-backed inventory must exclude library declarations." and
  exists(M3Callable callable | not callable.getFile().isJavaSourceFile())
}

from string message
where failure(message)
select message
