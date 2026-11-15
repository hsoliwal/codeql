/**
 * @name M3 Java native declaration inventory
 * @description Exports Java source native method signatures without inferring native implementations or ABI compatibility.
 * @kind table
 * @id java/m3/native-boundary-inventory
 * @tags maintainability
 */

import java
import lib.M3Java

from Method method
where
  isM3DeclaredJavaCallable(method) and
  method.hasModifier("native")
select m3CallableId(method) as callable_id,
  method.getDeclaringType().getQualifiedName() as declaring_type,
  method.getSignature() as signature, method.getReturnType().toString() as return_type,
  m3Visibility(method) as visibility, m3IsStatic(method) as is_static,
  method.getFile().getRelativePath() as source_path,
  method.getLocation().getStartLine() as start_line,
  method.getLocation().getStartColumn() as start_column,
  method.getLocation().getEndLine() as end_line, method.getLocation().getEndColumn() as end_column
  order by source_path, start_line, start_column, end_line, end_column, callable_id
