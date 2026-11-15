/**
 * Source-backed, callable-scoped semantic evidence for M3.
 *
 * These predicates describe extracted facts. They neither prove semantic
 * equivalence nor authorize a source transformation. An absent effect row is
 * not a purity result; indirect, native, reflective and library behavior can
 * remain unresolved.
 */

import java

/** A source declaration with a usable location in a Java source file. */
class M3Callable extends SrcCallable {
  M3Callable() {
    this.fromSource() and
    this.getFile().isJavaSourceFile() and
    this.getLocation().getStartLine() > 0 and
    this.getLocation().getStartColumn() > 0 and
    this.getLocation().getEndLine() >= this.getLocation().getStartLine() and
    this.getLocation().getEndColumn() > 0
  }
}

/** Gets an overload-sensitive declared identity for a callable. */
string callableIdentity(Callable callable) {
  result = callable.getDeclaringType().getQualifiedName() + "#" + callable.getSignature()
}

/** Gets the declared Java visibility of a callable. */
string visibility(M3Callable callable) {
  if callable.isPublic()
  then result = "public"
  else
    if callable.isProtected()
    then result = "protected"
    else
      if callable.isPrivate()
      then result = "private"
      else result = "package"
}

/** Gets a positive, declared modifier fact. */
string callableModifier(M3Callable callable) {
  callable.isStatic() and result = "static"
  or
  callable.isFinal() and result = "final"
  or
  callable.isAbstract() and result = "abstract"
  or
  callable.isNative() and result = "native"
  or
  callable.isSynchronized() and result = "synchronized"
  or
  callable.isVarargs() and result = "varargs"
}

/** Provides inventory and declared contract facts, without inferring behavior. */
predicate inventoryEvidence(M3Callable callable, string kind, string subject) {
  kind = "callable" and subject = callable.getMethodDescriptor()
  or
  kind = "contract/visibility" and subject = visibility(callable)
  or
  kind = "contract/return" and subject = callable.getReturnType().getTypeDescriptor()
  or
  kind = "contract/modifier" and subject = callableModifier(callable)
  or
  exists(int index |
    kind = "contract/parameter" and
    subject = index.toString() + ":" + callable.getParameterType(index).getTypeDescriptor()
  )
  or
  kind = "contract/throws" and
  subject = callable.getAThrownExceptionType().getQualifiedName()
}

/**
 * Provides declared call targets. Here "static" means the target bound by
 * extraction, not Java's static modifier and not complete runtime dispatch.
 */
predicate callEvidence(M3Callable callable, string kind, string subject) {
  exists(Call call |
    call.getCaller() = callable and
    kind = "call/static" and
    subject = callableIdentity(call.getCallee().getSourceDeclaration())
  )
}

/** Provides direct, local effect hints; it does not infer purity or transitive effects. */
predicate effectEvidence(M3Callable callable, string kind, string subject) {
  exists(Field field |
    callable.reads(field) and
    kind = "effect/read-field" and
    subject = field.getDeclaringType().getQualifiedName() + "#" + field.getName()
  )
  or
  exists(Field field |
    callable.writes(field) and
    kind = "effect/write-field" and
    subject = field.getDeclaringType().getQualifiedName() + "#" + field.getName()
  )
  or
  exists(Assignment assignment, ArrayAccess access |
    assignment.getEnclosingCallable() = callable and
    assignment.getDest() = access and
    kind = "effect/write-array" and
    subject = access.getArray().getType().getTypeDescriptor()
  )
  or
  exists(UnaryAssignExpr update, ArrayAccess access |
    update.getEnclosingCallable() = callable and
    update.getOperand() = access and
    kind = "effect/write-array" and
    subject = access.getArray().getType().getTypeDescriptor()
  )
  or
  exists(ThrowStmt statement |
    statement.getEnclosingCallable() = callable and
    kind = "effect/throw" and
    subject = statement.getExpr().getType().getTypeDescriptor()
  )
  or
  exists(SynchronizedStmt statement |
    statement.getEnclosingCallable() = callable and
    kind = "effect/monitor" and
    subject = "block"
  )
  or
  callable.isSynchronized() and kind = "effect/monitor" and subject = "method"
  or
  callable.isNative() and kind = "effect/native" and subject = callableIdentity(callable)
}
