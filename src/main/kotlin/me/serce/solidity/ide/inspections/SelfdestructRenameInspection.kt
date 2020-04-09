package me.serce.solidity.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import me.serce.solidity.ide.inspections.fixes.RenameFix
import me.serce.solidity.lang.psi.SolCallExpression
import me.serce.solidity.lang.psi.SolPrimaryExpression
import me.serce.solidity.lang.psi.SolVisitor

class SelfdestructRenameInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : SolVisitor() {
      override fun visitCallExpression(o: SolCallExpression) {
        inspectCall(o, holder)
      }
    }
  }

  private fun inspectCall(expr: SolCallExpression, holder: ProblemsHolder) {
    val base = expr.expression
    if (base is SolPrimaryExpression && base.varLiteral != null && base.text == "suicide") {
      holder.registerProblem(expr, "suicide is deprecated. rename to selfdestruct. EIP 6",
        RenameFix(expr, "selfdestruct"))
    }
  }

  override fun getID(): String = "suicide_deprecated"
}
