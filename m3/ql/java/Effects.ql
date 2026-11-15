/**
 * @name M3 Java local effect evidence
 * @description Exports direct field, array, throw, monitor and native evidence without certifying purity.
 * @kind table
 * @id java/m3/effects
 */

import M3Evidence

from M3Callable callable, string kind, string subject
where effectEvidence(callable, kind, subject)
select callable.getFile().getRelativePath() as path,
  callable.getDeclaringType().getPackage().getName() as package,
  callable.getDeclaringType().getQualifiedName() as owner, callable.getSignature() as signature,
  kind, subject, callable.getLocation().getStartLine() as startLine,
  callable.getLocation().getStartColumn() as startColumn,
  callable.getLocation().getEndLine() as endLine, callable.getLocation().getEndColumn() as endColumn
