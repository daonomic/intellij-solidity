package me.serce.solidity.lang.core.resolve

import me.serce.solidity.lang.psi.*
import org.intellij.lang.annotations.Language

class SolFunctionResolveTest : SolResolveTestBase() {
  fun testResolveFunction() = checkByCode("""
        contract B {
            uint public doit2;
        
            function doit2() {
                    //x
            }

            function doit() {
                doit2();
                //^
            }
        }
  """)

  fun testResolveFunctionWithParameters() = checkByCode("""
        contract B {
            function doit2(int a, int b) {
                    //x
            }


            function doit() {
                doit2(1, 2);
                //^
            }
        }
  """)

  fun testResolveFunctionFromParent() = checkByCode("""
        contract Base {
            function doit2() {
            }
        }

        contract A is Base {
            function doit2() {
                    //x
            }
        }

        contract B is A {
            function doit() {
                doit2();
                //^
            }
        }
  """)

  fun testResolveGlobal() {
    val (refElement, _) = resolveInCode<SolCallExpression>("""
        contract B {
            function doit() {
                assert(true);
                 //^
            }
        }
    """)

    val resolved = refElement.reference?.resolve()
    assertTrue(resolved is SolFunctionDefinition)
    if (resolved is SolFunctionDefinition) {
      assertEquals(resolved.name, "assert")
    }
  }

  fun testResolveContractConstructor() = checkByCode("""
        contract A {
               //x
        }

        contract B {
            function doit() {
                A a = A(1);
                    //^
            }
        }
  """)

  fun testResolveVarAsFunction() = checkByCodeInternal<SolVarLiteral, SolNamedElement>("""
        contract B {
            uint256 doit;
        
            function doit(uint16) {
                    //x
            }

            function test() {
                doit(1 + 1);
                //^
            }
        }
  """)

  fun testResolveFunctionSameNumberOfArguments() = checkByCode("""
        contract B {
            function doit(uint16) {
                    //x
            }

            function doit(string) {

            }

            function test() {
                doit(1 + 1);
                //^
            }
        }
  """)

  fun testResolveFunctionUintWithUnderscores() = checkByCode("""
        contract B {
            function doit(uint16) {
                    //x
            }

            function test() {
                doit(1_000);
                //^
            }
        }
  """)

  fun testResolveFunctionUintWithExponent() = checkByCode("""
        contract B {
            function doit(uint256) {
                    //x
            }

            function test() {
                doit(10 ** 18);
                //^
            }
        }
  """)

  fun testResolveFunctionUintWithScientificNotation() = checkByCode("""
        contract B {
            function doit(uint256) {
                    //x
            }

            function test() {
                doit(2e20);
                //^
            }
        }
  """)

  fun testResolveFunctionEnum() = checkByCode("""
        contract B {
            enum Test {
                ONE
            }

            function doit(Test) {
                    //x
            }

            function test() {
                doit(Test.ONE);
                //^
            }
        }
  """)

  override fun checkByCode(@Language("Solidity") code: String) {
    checkByCodeInternal<SolCallExpression, SolNamedElement>(code)
  }
}
