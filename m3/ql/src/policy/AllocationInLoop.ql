/**
 * @name Review explicit allocation in a loop
 * @description Finds explicit allocation expressions syntactically contained in a loop in the same callable.
 * @kind problem
 * @problem.severity recommendation
 * @precision high
 * @id java/m3/allocation-in-loop
 * @tags maintainability
 */

import java

from Expr allocation
where
  allocation.getFile().isJavaSourceFile() and
  (allocation instanceof NewClassExpr or allocation instanceof ArrayCreationExpr) and
  exists(LoopStmt loop |
    loop = allocation.getAnEnclosingStmt() and
    loop.getEnclosingCallable() = allocation.getEnclosingCallable()
  )
select allocation,
  "Review this explicit allocation syntactically inside a loop in the same callable; runtime cost and safe changes require measurement."
