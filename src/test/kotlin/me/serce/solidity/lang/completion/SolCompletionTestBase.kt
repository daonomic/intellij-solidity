package me.serce.solidity.lang.completion

import me.serce.solidity.utils.SolTestBase
import org.intellij.lang.annotations.Language

abstract class SolCompletionTestBase : SolTestBase() {
  protected fun checkCompletion(required: Set<String>, @Language("Solidity") code: String, strict: Boolean = false): List<String> {
    InlineFile(code).withCaret()
    val variants = myFixture.completeBasic()
    checkNotNull(variants) {
      "Expected completions that contain $required, but no completions found"
    }
    val result = variants.map { it.lookupString }
    if (strict) {
      assertEquals(required.toHashSet(), result.toHashSet())
    } else {
      assertTrue("$result doesn't contain $required", result.containsAll(required))
    }
    return result
  }
}
