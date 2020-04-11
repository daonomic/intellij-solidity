package me.serce.solidity.lang.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import me.serce.solidity.lang.completion.SolCompleter
import me.serce.solidity.lang.psi.*
import me.serce.solidity.lang.resolve.SolResolver
import me.serce.solidity.lang.resolve.canBeApplied
import me.serce.solidity.lang.types.ContextType
import me.serce.solidity.lang.types.SolMember
import me.serce.solidity.lang.types.Usage
import me.serce.solidity.lang.types.findContract

class SolUserDefinedTypeNameReference(element: SolUserDefinedTypeName) : SolReferenceBase<SolUserDefinedTypeName>(element), SolReference {
  override fun multiResolve() = SolResolver.resolveTypeNameUsingImports(element)

  override fun getVariants() = SolCompleter.completeTypeName(element)
}

class SolVarLiteralReference(element: SolVarLiteral) : SolReferenceBase<SolVarLiteral>(element), SolReference {
  override fun multiResolve() = SolResolver.resolveVarLiteral(element)

  override fun getVariants() = SolCompleter.completeLiteral(element).toList().toTypedArray()
}

class SolModifierReference(
  element: SolReferenceElement,
  private val modifierElement: SolModifierInvocationElement
) : SolReferenceBase<SolReferenceElement>(element), SolReference {

  override fun calculateDefaultRangeInElement() = modifierElement.parentRelativeRange

  override fun multiResolve(): List<SolNamedElement> {
    val contract = modifierElement.findContract()!!
    val superNames: List<String> = (contract.collectSupers.map { it.name } + contract.name).filterNotNull()
    return SolResolver.resolveModifier(modifierElement)
      .filter { it.contract.name in superNames }
  }

  override fun getVariants() = SolCompleter.completeModifier(modifierElement)
}

class SolNewExpressionReference(element: SolNewExpression) : SolReferenceBase<SolNewExpression>(element), SolReference {

  override fun calculateDefaultRangeInElement(): TextRange {
    return element.referenceNameElement.parentRelativeRange
  }

  override fun multiResolve(): Collection<PsiElement> {
    val types = SolResolver.resolveTypeNameUsingImports(element)
    return types
      .filterIsInstance(SolContractDefinition::class.java)
      .flatMap {
        val constructors = it.findConstructors()
        if (constructors.isEmpty()) {
          listOf(it)
        } else {
          constructors
        }
      }
  }
}

fun SolContractDefinition.findConstructors(): List<SolElement> {
  return if (this.constructorDefinitionList.isNotEmpty()) {
    this.constructorDefinitionList
  } else {
    this.functionDefinitionList
      .filter { it.name == this.name }
  }
}

class SolDotExpressionReference(element: SolDotExpression) : SolReferenceBase<SolDotExpression>(element) {
  override fun calculateDefaultRangeInElement(): TextRange {
    return element.memberFunctionCall?.identifier?.rangeRelativeTo(element)
      ?: element.identifier?.parentRelativeRange
      ?: super.calculateDefaultRangeInElement()
  }

  override fun multiResolve(): Collection<PsiElement> {
    return resolveMembers()
      .mapNotNull { it.resolveElement() }
  }

  override fun getVariants(): Array<out Any> {
    return SolCompleter.completeMemberAccess(element)
  }

  fun resolveMembers(): List<SolMember> {
    val expr = element.expression

    val contextType = when {
      expr is SolPrimaryExpression && expr.varLiteral?.name == "super" -> ContextType.SUPER
      else -> ContextType.EXTERNAL
    }

    val byName = SolResolver.resolveMembers(expr)
      .filter { it.getName() == element.name }

    val functionCall = element.memberFunctionCall
    val resolved = if (functionCall != null) {
      byName
        .filter { it.getPossibleUsage(contextType) == Usage.CALLABLE }
        .filterIsInstance<SolCallable>()
        .filter { it.canBeApplied(functionCall.functionCallArguments?.expressionList ?: emptyList()) }
        .filterIsInstance<SolMember>()
        .toList()
    } else {
      byName
        .filter { it.getPossibleUsage(contextType) == Usage.VARIABLE }
        .toList()
    }
    return if (resolved.size == 1) {
      resolved
    } else {
      byName.toList()
    }
  }
}
