/**
 * @name M3 Java static call dependencies
 * @description Exports Java source call sites and their statically resolved callable targets.
 * @kind table
 * @id java/m3/call-dependencies
 * @tags maintainability
 */

import java
import lib.M3Java

from Call call, Callable caller, Callable callee, string kind
where
  call.getFile().isJavaSourceFile() and
  kind = m3CallKind(call) and
  caller = call.getCaller().getSourceDeclaration() and
  callee = call.getCallee().getSourceDeclaration() and
  not callee instanceof InitializerMethod
select m3CallableId(caller) as caller_id,
  caller.getDeclaringType().getQualifiedName() as caller_declaring_type,
  caller.getSignature() as caller_signature, m3CallableId(callee) as callee_id,
  callee.getDeclaringType().getQualifiedName() as callee_declaring_type,
  callee.getSignature() as callee_signature, kind as call_kind,
  call.getFile().getRelativePath() as source_path, call.getLocation().getStartLine() as start_line,
  call.getLocation().getStartColumn() as start_column, call.getLocation().getEndLine() as end_line,
  call.getLocation().getEndColumn() as end_column order by
    source_path, start_line, start_column, end_line, end_column, caller_id, callee_id, call_kind
