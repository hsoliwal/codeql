/**
 * @name M3 Java callable inventory and declared contracts
 * @description Exports source-backed callable identities and declared contract evidence.
 * @kind table
 * @id java/m3/inventory
 */

import M3Evidence

from M3Callable callable, string kind, string subject
where inventoryEvidence(callable, kind, subject)
select callable.getFile().getRelativePath() as path,
  callable.getDeclaringType().getPackage().getName() as package,
  callable.getDeclaringType().getQualifiedName() as owner, callable.getSignature() as signature,
  kind, subject, callable.getLocation().getStartLine() as startLine,
  callable.getLocation().getStartColumn() as startColumn,
  callable.getLocation().getEndLine() as endLine, callable.getLocation().getEndColumn() as endColumn
