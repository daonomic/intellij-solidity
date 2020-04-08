package me.serce.solidity.lang.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import me.serce.solidity.lang.completion.SolCompleter
import me.serce.solidity.lang.psi.*
import me.serce.solidity.lang.resolve.SolResolver
import me.serce.solidity.lang.resolve.canBeApplied
import me.serce.solidity.lang.types.*
import me.serce.solidity.wrap

class SolUserDefinedTypeNameReference(element: SolUserDefinedTypeName) : SolReferenceBase<SolUserDefinedTypeName>(element), SolReference {
  override fun multiResolve() = SolResolver.resolveTypeNameUsingImports(element)

  override fun getVariants() = SolCompleter.completeTypeName(element)
}

class SolVarLiteralReference(element: SolVarLiteral) : SolReferenceBase<SolVarLiteral>(element), SolReference {
  override fun multiResolve() = SolResolver.resolveVarLiteralReference(element)

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

/*
class SolMemberAccessReference(element: SolMemberAccessExpression) : SolReferenceBase<SolMemberAccessExpression>(element), SolReference {
  override fun calculateDefaultRangeInElement(): TextRange {
    return element.identifier?.parentRelativeRange ?: super.calculateDefaultRangeInElement()
  }

  override fun multiResolve() = SolResolver.resolveMemberAccess(element)
    .mapNotNull { it.resolveElement() }

  override fun getVariants() = SolCompleter.completeMemberAccess(element)
}
*/

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
    return element.identifier?.parentRelativeRange
      ?: element.memberFunctionCall?.parentRelativeRange
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

class SolCallExpressionReference(element: SolCallExpression) : SolReferenceBase<SolCallExpression>(element) {

  override fun multiResolve(): Collection<PsiElement> {
    return resolveFunctionCallAndFilter()
      .mapNotNull { it.resolveElement() }
  }

  fun resolveFunctionCallAndFilter(): List<SolCallable> {
    return resolveFunctionCall()
      .filter { it.canBeApplied(element.functionCallArguments?.expressionList ?: emptyList()) }
  }

  fun resolveFunctionCall(): Collection<SolCallable> {
    val regular = element.varLiteral?.let { SolResolver.resolveVarLiteral(it) }
      ?.filter { it !is SolStateVariableDeclaration }
      ?.filterIsInstance<SolCallable>()
      ?: emptyList()
    val casts = resolveElementaryTypeCasts()
    return regular + casts
  }

  private fun resolveElementaryTypeCasts(): Collection<SolCallable> {
    return element.elementaryTypeName
      ?.let {
        val type = getSolType(it)
        object : SolCallable {
          override fun resolveElement(): SolNamedElement? = null
          override fun parseParameters(): List<Pair<String?, SolType>> = listOf(null to SolUnknown)
          override fun parseType(): SolType = type
          override val callablePriority: Int = 1000
          override fun getName(): String? = null
        }
      }
      .wrap()
  }
}

/*
class SolFunctionCallReference(element: SolFunctionCallExpression) : SolReferenceBase<SolFunctionCallExpression>(element), SolReference {
  override fun calculateDefaultRangeInElement(): TextRange {
    return element.referenceNameElement.parentRelativeRange
  }

  fun resolveFunctionCall(): Collection<SolCallable> {
    val resolved: Collection<SolCallable> = when (val expr = element.expression) {
      is SolPrimaryExpression -> {
        val regular = expr.varLiteral?.let { SolResolver.resolveVarLiteral(it) }
          ?.filter { it !is SolStateVariableDeclaration }
          ?.filterIsInstance<SolCallable>()
          ?: emptyList()
        val casts = resolveElementaryTypeCasts(expr)
        regular + casts
      }
      is SolMemberAccessExpression -> {
        resolveMemberFunctions(expr) + resolveFunctionCallUsingLibraries(expr)
      }
      else ->
        emptyList()
    }
    return removeOverrides(resolved.groupBy { it.callablePriority }.entries.minBy { it.key }?.value ?: emptyList())
  }

  private fun removeOverrides(callables: Collection<SolCallable>): Collection<SolCallable> {
    val test = callables.filterIsInstance<SolFunctionDefinition>()
    return callables
      .filter {
        when (it) {
          is SolFunctionDefinition -> SolFunctionResolver.collectOverrides(it).intersect(test).isEmpty()
          else -> true
        }
      }
  }

  private fun resolveElementaryTypeCasts(expr: SolPrimaryExpression): Collection<SolCallable> {
    return expr.elementaryTypeName
      ?.let {
        val type = getSolType(it)
        object : SolCallable {
          override fun resolveElement(): SolNamedElement? = null
          override fun parseParameters(): List<Pair<String?, SolType>> = listOf(null to SolUnknown)
          override fun parseType(): SolType = type
          override val callablePriority: Int = 1000
          override fun getName(): String? = null
        }
      }
      .wrap()
  }

  private fun resolveMemberFunctions(expression: SolMemberAccessExpression): Collection<SolCallable> {
    val name = expression.identifier?.text
    return if (name != null) {
      expression.expression.getMembers()
        .filterIsInstance<SolCallable>()
        .filter { it.getName() == name }
    } else {
      emptyList()
    }
  }

  private fun resolveFunctionCallUsingLibraries(expression: SolMemberAccessExpression): Collection<SolCallable> {
    val name = expression.identifier?.text
    return if (name != null) {
      val type = expression.expression.type
      if (type != SolUnknown) {
        val contract = expression.findContract()
        val superContracts = contract
          ?.collectSupers
          ?.flatMap { SolResolver.resolveTypeNameUsingImports(it) }
          ?.filterIsInstance<SolContractDefinition>()
          ?: emptyList()
        val libraries = (superContracts + contract.wrap())
          .flatMap { it.usingForDeclarationList }
          .filter {
            val usingType = it.type
            usingType == null || usingType == type
          }
          .map { it.library }
        return libraries
          .distinct()
          .flatMap { it.functionDefinitionList }
          .filter { it.name == name }
          .filter {
            val firstParam = it.parameters.firstOrNull()
            if (firstParam == null) {
              false
            } else {
              getSolType(firstParam.typeName).isAssignableFrom(type)
            }
          }
          .map { it.toLibraryCallable() }
      } else {
        emptyList()
      }
    } else {
      emptyList()
    }
  }

  private fun SolFunctionDefinition.toLibraryCallable(): SolCallable {
    return object : SolCallable {
      override fun parseParameters(): List<Pair<String?, SolType>> = this@toLibraryCallable.parseParameters().drop(1)
      override fun parseType(): SolType = this@toLibraryCallable.parseType()
      override fun resolveElement() = this@toLibraryCallable
      override fun getName() = name
      override val callablePriority = 0
    }
  }

  override fun multiResolve(): Collection<PsiElement> {
    return resolveFunctionCallAndFilter()
      .mapNotNull { it.resolveElement() }
  }

  fun resolveFunctionCallAndFilter(): List<SolCallable> {
    return resolveFunctionCall()
      .filter { it.canBeApplied(element.functionCallArguments) }
  }
}
*/
