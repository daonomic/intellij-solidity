package me.serce.solidity.lang.resolve

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import me.serce.solidity.lang.core.SolidityFile
import me.serce.solidity.lang.psi.*
import me.serce.solidity.lang.stubs.SolGotoClassIndex
import me.serce.solidity.lang.stubs.SolModifierIndex
import me.serce.solidity.lang.types.*
import me.serce.solidity.nullIfError
import me.serce.solidity.wrap

object SolResolver {
  fun resolveTypeNameUsingImports(element: PsiElement): Set<SolNamedElement> = CachedValuesManager.getCachedValue(element) {
    val result = resolveContractUsingImports(element, element.containingFile, true) +
      resolveEnum(element) +
      resolveStruct(element)
    CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
  }

  /**
   * @param withAliases aliases are not recursive, so count them only at the first level of recursion
   */
  private fun resolveContractUsingImports(element: PsiElement, file: PsiFile, withAliases: Boolean): Set<SolContractDefinition> =
    RecursionManager.doPreventingRecursion(ResolveContractKey(element.nameOrText, file), true) {
      if (element is SolUserDefinedTypeName && element.findIdentifiers().size > 1) {
        emptySet()
      } else {
        val inFile = file.children
          .filterIsInstance<SolContractDefinition>()
          .filter { it.name == element.nameOrText }

        val resolvedViaAlias = when (withAliases) {
          true -> file.children
            .filterIsInstance<SolImportDirective>()
            .mapNotNull { directive ->
              directive.importAliasedPairList
                .firstOrNull { aliasPair -> aliasPair.importAlias?.name == element.nameOrText }
                ?.let { aliasPair ->
                  directive.importPath?.reference?.resolve()?.let { resolvedFile ->
                    aliasPair.userDefinedTypeName to resolvedFile
                  }
                }
            }.flatMap { (alias, resolvedFile) ->
              resolveContractUsingImports(alias, resolvedFile.containingFile, false)
            }
          else -> emptyList()
        }

        val imported = file.children
          .filterIsInstance<SolImportDirective>()
          .mapNotNull { nullIfError { it.importPath?.reference?.resolve()?.containingFile } }
          .flatMap { resolveContractUsingImports(element, it, false) }

        (inFile + resolvedViaAlias + imported).toSet()
      }
    } ?: emptySet()

  private fun resolveEnum(element: PsiElement): Set<SolNamedElement> =
    resolveInnerType<SolEnumDefinition>(element) { it.enumDefinitionList }

  private fun resolveStruct(element: PsiElement): Set<SolNamedElement> =
    resolveInnerType<SolStructDefinition>(element) { it.structDefinitionList }

  private fun <T : SolNamedElement> resolveInnerType(element: PsiElement, f: (SolContractDefinition) -> List<T>): Set<T> {
    val inheritanceSpecifier = element.parentOfType<SolInheritanceSpecifier>()
    return if (inheritanceSpecifier != null) {
      emptySet()
    } else {
      val names = if (element is SolUserDefinedTypeNameElement) {
        element.findIdentifiers()
      } else {
        element.wrap()
      }
      when {
        names.size > 2 -> emptySet()
        names.size > 1 -> resolveTypeNameUsingImports(names[0])
          .filterIsInstance<SolContractDefinition>()
          .firstOrNull()
          ?.let { resolveInnerType(it, names[1].nameOrText!!, f) }
          ?: emptySet()

        else -> element.parentOfType<SolContractDefinition>()
          ?.let { resolveInnerType(it, names[0].nameOrText!!, f) }
          ?: emptySet()
      }
    }
  }

  private val PsiElement.nameOrText
    get() = if (this is PsiNamedElement) {
      this.name
    } else {
      this.text
    }

  private fun <T : SolNamedElement> resolveInnerType(contract: SolContractDefinition, name: String, f: (SolContractDefinition) -> List<T>): Set<T> {
    val supers = contract.collectSupers
      .mapNotNull { it.reference?.resolve() }.filterIsInstance<SolContractDefinition>() + contract
    return supers.flatMap(f)
      .filter { it.name == name }
      .toSet()
  }

  fun resolveTypeName(element: SolReferenceElement): Collection<SolNamedElement> = StubIndex.getElements(
    SolGotoClassIndex.KEY,
    element.referenceName,
    element.project,
    null,
    SolNamedElement::class.java
  )

  fun resolveModifier(modifier: SolModifierInvocationElement): List<SolModifierDefinition> = StubIndex.getElements(
    SolModifierIndex.KEY,
    modifier.text,
    modifier.project,
    null,
    SolNamedElement::class.java
  ).filterIsInstance<SolModifierDefinition>()
    .toList()

  fun resolveVarLiteral(element: SolVarLiteral): List<SolNamedElement> {
    return when (element.name) {
      "this" -> element.findContract()
        .wrap()
      "super" -> element.findContract()
        ?.supers
        ?.flatMap { resolveTypeNameUsingImports(it) }
        ?: emptyList()
      else -> {
        val byName = lexicalDeclarations(element)
          .filter { it.name == element.name }
          .toList()
        val parent = element.parent?.parent
        if (parent is SolCallExpression) {
          val calls = byName.asSequence()
            .filter { it !is SolStateVariableDeclaration }
            .filterIsInstance<SolCallable>()
            .filter { it.canBeApplied(parent.functionCallArguments?.expressionList ?: emptyList()) }
            .mapNotNull { it.resolveElement() }
            .toList()
          if (calls.size == 1) {
            calls
          } else {
            byName
          }
        } else {
          byName
        }
      }
    }
  }

  fun resolveMembers(expr: SolExpression): Sequence<SolMember> {
    return when {
      expr is SolPrimaryExpression && expr.varLiteral?.name == "super" -> {
        val contract = expr.findContract()
        contract?.let { resolveContractMembers(it, true) }
          ?: emptySequence()
      }
      else -> {
        sequenceOf(
          expr.type.getMembers(expr.project),
          resolveMembersUsingLibraries(expr)
        ).flatten()
      }
    }
  }

  private fun resolveMembersUsingLibraries(expression: SolExpression): Sequence<SolMember> {
    val type = expression.type
    return if (type != SolUnknown) {
      val contract = expression.findContract()
      val superContracts = contract
        ?.collectSupers
        ?.flatMap { resolveTypeNameUsingImports(it) }
        ?.filterIsInstance<SolContractDefinition>()
        ?: emptyList()
      val libraries = sequenceOf(contract.wrap(), superContracts)
        .flatten()
        .flatMap { it.usingForDeclarationList.asSequence() }
        .filter {
          val usingType = it.type
          usingType == null || usingType == type
        }
        .map { it.library }
      return libraries
        .distinct()
        .flatMap { it.functionDefinitionList.asSequence() }
        .filter {
          val firstParam = it.parameters.firstOrNull()
          if (firstParam == null) {
            false
          } else {
            getSolType(firstParam.typeName).isAssignableFrom(type)
          }
        }
        .map { it.toLibraryMember() }
    } else {
      emptySequence()
    }
  }

  private fun SolFunctionDefinition.toLibraryMember(): SolMember {
    return object : SolCallable, SolMember {
      override fun parseParameters(): List<Pair<String?, SolType>> = this@toLibraryMember.parseParameters().drop(1)
      override fun parseType(): SolType = this@toLibraryMember.parseType()
      override fun resolveElement() = this@toLibraryMember
      override fun getPossibleUsage(contextType: ContextType): Usage = Usage.CALLABLE
      override fun getName() = name
      override val callablePriority = 0
    }
  }

  fun resolveContractMembers(contract: SolContractDefinition, skipThis: Boolean = false): Sequence<SolMember> {
    val members = if (!skipThis)
      sequenceOf(contract.stateVariableDeclarationList as List<SolMember>, contract.functionDefinitionList).flatten()
    else
      emptySequence()
    val signatures = members.mapNotNull { it.toSignature() }.toSet()
    return members + contract.supers.asSequence()
      .map { resolveTypeName(it).firstOrNull() }
      .filterIsInstance<SolContractDefinition>()
      .flatMap { resolveContractMembers(it) }
      .filter { !signatures.contains(it.toSignature()) }
  }

  fun lexicalDeclarations(place: PsiElement, stop: (PsiElement) -> Boolean = { false }): Sequence<SolNamedElement> {
    val globalType = SolInternalTypeFactory.of(place.project).globalType
    return lexicalDeclarations(globalType.ref, place) + lexicalDeclRec(place, stop).distinct()
  }

  private fun lexicalDeclRec(place: PsiElement, stop: (PsiElement) -> Boolean): Sequence<SolNamedElement> {
    return place.ancestors
      .drop(1) // current element might not be a SolElement
      .takeWhileInclusive { it is SolElement && !stop(it) }
      .flatMap { lexicalDeclarations(it, place) }
  }

  private fun lexicalDeclarations(scope: PsiElement, place: PsiElement): Sequence<SolNamedElement> {
    return when (scope) {
      is SolVariableDeclaration -> {
        scope.declarationList?.declarationItemList?.filterIsInstance<SolNamedElement>()?.asSequence()
          ?: scope.typedDeclarationList?.typedDeclarationItemList?.filterIsInstance<SolNamedElement>()?.asSequence()
          ?: sequenceOf(scope)
      }
      is SolVariableDefinition -> lexicalDeclarations(scope.firstChild, place)

      is SolStateVariableDeclaration -> sequenceOf(scope)
      is SolContractDefinition -> {
        val childrenScope = sequenceOf(
          scope.enumDefinitionList as List<SolNamedElement>,
          scope.structDefinitionList,
          scope.eventDefinitionList,
          scope.stateVariableDeclarationList,
          scope.functionDefinitionList
        ).flatten()
        val signatures = childrenScope
          .mapNotNull { it.toSignature() }
          .toSet()
        val extendsScope = scope.supers.asSequence()
          .map { resolveTypeName(it).firstOrNull() }
          .filterNotNull()
          .flatMap { lexicalDeclarations(it, place) }
          .filter { !signatures.contains(it.toSignature()) }
        childrenScope + extendsScope
      }
      is SolFunctionDefinition -> {
        scope.parameters.asSequence() +
          (scope.returns?.parameterDefList?.asSequence() ?: emptySequence())
      }
      is SolConstructorDefinition -> {
        scope.parameterList?.parameterDefList?.asSequence() ?: emptySequence()
      }
      is SolEnumDefinition -> sequenceOf(scope)

      is SolStatement -> {
        scope.children.asSequence()
          .map { lexicalDeclarations(it, place) }
          .flatten()
      }

      is SolBlock -> {
        scope.statementList.asSequence()
          .map { lexicalDeclarations(it, place) }
          .flatten()
      }

      is SolidityFile -> {
        RecursionManager.doPreventingRecursion(scope.name, true) {
          val contracts = scope.children.asSequence()
            .filterIsInstance<SolContractDefinition>()
          val imports = scope.children.asSequence().filterIsInstance<SolImportDirective>()
            .mapNotNull { nullIfError { it.importPath?.reference?.resolve()?.containingFile } }
            .mapNotNull { lexicalDeclarations(it, place) }
            .flatten()
          imports + contracts
        } ?: emptySequence()
      }

      is SolTupleStatement -> {
        scope.variableDeclaration?.let {
          val declarationList = it.declarationList
          val typedDeclarationList = it.typedDeclarationList
          when {
            declarationList != null -> declarationList.declarationItemList.asSequence()
            typedDeclarationList != null -> typedDeclarationList.typedDeclarationItemList.asSequence()
            else -> emptySequence()
          }
        } ?: emptySequence()
      }

      else -> emptySequence()
    }
  }
}

fun SolNamedElement.toSignature(): Signature? {
  val name = this.name
  return if (name != null && this is SolFunctionDefinition) {
    Signature(name, this.parseParameters().map { it.second })
  } else if (name != null) {
    Signature(name, emptyList())
  } else {
    null
  }
}

data class ResolveContractKey(val name: String?, val file: PsiFile)

private fun <T> Sequence<T>.takeWhileInclusive(pred: (T) -> Boolean): Sequence<T> {
  var shouldContinue = true
  return takeWhile {
    val result = shouldContinue
    shouldContinue = pred(it)
    result
  }
}

fun SolCallable.canBeApplied(arguments: List<SolExpression>): Boolean {
  val callArgumentTypes = arguments.map { it.type }
  val parameters = parseParameters()
    .map { it.second }
  if (parameters.size != callArgumentTypes.size)
    return false
  return !parameters.zip(callArgumentTypes)
    .any { (paramType, argumentType) ->
      paramType != SolUnknown && !paramType.isAssignableFrom(argumentType)
    }
}

fun SolMember.toSignature(): Signature? {
  val name = getName()
  val params = if (this is SolCallable) {
    this.parseParameters().map { it.second }
  } else {
    emptyList()
  }
  return if (name != null) {
    Signature(name, params)
  } else {
    null
  }
}
