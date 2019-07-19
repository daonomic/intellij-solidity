package me.serce.solidity.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import me.serce.solidity.lang.psi.SolFunctionCallExpression
import me.serce.solidity.lang.psi.SolVisitor
import me.serce.solidity.lang.resolve.ref.SolFunctionCallReference

class ResolveFunctionInspection : LocalInspectionTool() {
  override fun getDisplayName(): String = ""

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : SolVisitor() {
      override fun visitFunctionCallExpression(o: SolFunctionCallExpression) {
        val ref = o.reference
        if (ref is SolFunctionCallReference) {
          val resolved = ref.multiResolve()
          if (resolved.isEmpty()) {
            holder.registerProblem(o, "Function call not resolved")
          } else if (resolved.size > 1) {
            holder.registerProblem(o, "Function call resolved to more than 1 function")
          }
        }
      }
    }
  }
}
