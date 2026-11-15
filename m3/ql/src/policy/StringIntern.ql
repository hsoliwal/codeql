/**
 * @name Review String.intern use
 * @description Identifies resolved java.lang.String.intern() calls for an explicit review policy.
 * @kind problem
 * @problem.severity recommendation
 * @precision high
 * @id java/m3/string-intern
 * @tags maintainability
 */

import java

from MethodCall call
where
  call.getFile().isJavaSourceFile() and
  call.getMethod().hasQualifiedName("java.lang", "String", "intern") and
  call.getMethod().hasNoParameters()
select call,
  "Review this call to java.lang.String.intern(); assess the string-pool policy and measured workload."
