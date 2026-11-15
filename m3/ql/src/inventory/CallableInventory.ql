/**
 * @name M3 Java source callable inventory
 * @description Exports declared public and protected Java source callable signatures and source locations.
 * @kind table
 * @id java/m3/callable-inventory
 * @tags maintainability
 */

import java
import lib.M3Java

from Callable callable
where
  isM3DeclaredJavaCallable(callable) and
  (callable.isPublic() or callable.isProtected())
select m3CallableId(callable) as callable_id, m3CallableKind(callable) as kind,
  callable.getDeclaringType().getQualifiedName() as declaring_type,
  callable.getSignature() as signature, callable.getReturnType().toString() as return_type,
  m3Visibility(callable) as visibility, callable.getFile().getRelativePath() as source_path,
  callable.getLocation().getStartLine() as start_line,
  callable.getLocation().getStartColumn() as start_column,
  callable.getLocation().getEndLine() as end_line,
  callable.getLocation().getEndColumn() as end_column order by
    source_path, start_line, start_column, end_line, end_column, callable_id
