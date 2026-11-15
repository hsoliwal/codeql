/** Shared declaration and identity conventions for M3 Java evidence tables. */

import java

/** Holds for a declared Java source callable, excluding implicit model holders. */
predicate isM3DeclaredJavaCallable(Callable callable) {
  callable.getCompilationUnit().isJavaSourceFile() and
  callable.isSourceDeclaration() and
  not callable.isCompilerGenerated() and
  not callable instanceof InitializerMethod and
  not callable.(Constructor).isDefaultConstructor() and
  not exists(FunctionalExpr functional | functional.asMethod() = callable)
}

/** Gets the declaration kind, also distinguishing a constructor from a same-name method. */
string m3CallableKind(Callable callable) {
  callable instanceof Constructor and result = "constructor"
  or
  callable instanceof Method and result = "method"
}

private string sourceAnchor(Callable callable) {
  if callable.fromSource()
  then
    result =
      "@" + callable.getFile().getRelativePath() + ":" + callable.getLocation().getStartLine() + ":"
        + callable.getLocation().getStartColumn()
  else result = ""
}

/**
 * Gets an opaque snapshot identity for the source declaration of a callable.
 * Source positions disambiguate anonymous and local declarations. Binary targets
 * use semantic names only. This is not an ABI symbol or a cross-revision identifier.
 */
string m3CallableId(Callable callable) {
  exists(Callable declaration | declaration = callable.getSourceDeclaration() |
    result =
      declaration.getDeclaringType().getQualifiedName() + "#" + m3CallableKind(declaration) + ":" +
        declaration.getSignature() + sourceAnchor(declaration)
  )
}

/** Gets declared access visibility; this does not establish type accessibility. */
string m3Visibility(Callable callable) {
  if callable.isPublic()
  then result = "public"
  else
    if callable.isProtected()
    then result = "protected"
    else
      if callable.isPrivate()
      then result = "private"
      else result = "package-private"
}

/** Gets whether a declaration is static as a primitive table cell. */
boolean m3IsStatic(Callable callable) {
  if callable.isStatic() then result = true else result = false
}

/**
 * Gets the source-call category, excluding implicit functional-object creation.
 * The Java model can still include implicit superclass constructor invocations.
 */
string m3CallKind(Call call) {
  call instanceof MethodCall and result = "method"
  or
  call instanceof NewClassExpr and result = "new"
  or
  call instanceof ThisConstructorInvocationStmt and result = "this-constructor"
  or
  call instanceof SuperConstructorInvocationStmt and result = "super-constructor"
}
