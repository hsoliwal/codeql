/**
 * @name M3 Java declared call evidence
 * @description Exports declared call targets as callable-scoped evidence, without resolving runtime dispatch.
 * @kind table
 * @id java/m3/calls
 */

import M3Evidence

from M3Callable callable, string kind, string subject
where callEvidence(callable, kind, subject)
select callable.getFile().getRelativePath() as path,
  callable.getDeclaringType().getPackage().getName() as package,
  callable.getDeclaringType().getQualifiedName() as owner, callable.getSignature() as signature,
  kind, subject, callable.getLocation().getStartLine() as startLine,
  callable.getLocation().getStartColumn() as startColumn,
  callable.getLocation().getEndLine() as endLine, callable.getLocation().getEndColumn() as endColumn
