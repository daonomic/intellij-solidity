package me.serce.solidity.lang.core.resolve

import junit.framework.TestCase
import me.serce.solidity.lang.psi.SolContractDefinition
import me.serce.solidity.lang.psi.SolExpression
import me.serce.solidity.lang.psi.SolStructDefinition
import me.serce.solidity.lang.types.*
import org.intellij.lang.annotations.Language

class InferenceTest : SolResolveTestBase() {

  fun testCastContract() {
    InlineFile(
      code = """
         contract Contract {
                  //x

         }
      """,
      name = "base.sol"
    )

    val (contract, _) = findElementAndDataInEditor<SolContractDefinition>("x")
    checkType(SolContract(contract), """
        import './base.sol';
        contract Test  {
            function test() {
                var test = Contract(address(this));
                test;
               //^
            }
        }""")
  }

  fun testNegateBoolean() {
    checkType(SolBoolean, """
        contract Contract {
            function test() {
                var test = !true;
                test;
               //^
            }
        }""")
  }

  fun testCastAddress() {
    checkType(SolAddress, """
        contract Contract {
            function test() {
                var test = address(this);
                test;
               //^
            }
        }""")
  }

  fun testFunctionType() {
    checkType(SolString, """
        contract Contract {
            function (uint) pure returns (string) f;
        
            function test() {
                var test = f(0);
                test;
               //^
            }
        }""")
  }

  fun testFunctionType2() {
    checkType(SolString, """
        contract Contract {
            mapping(uint => function (uint) pure returns (string)) fs;
        
            function test() {
                var test = fs[1](0);
                test;
               //^
            }
        }""")
  }

  fun testStorageStruct() {
    InlineFile(
      code = """
         contract StructBase {
           struct Struct {}
                  //x
         }
      """,
      name = "base.sol"
    )

    val (struct, _) = findElementAndDataInEditor<SolStructDefinition>("x")
    checkType(SolStruct(struct), """
        import './base.sol';
        contract Contract is StructBase {
            function test() {
                Struct storage s;
                var test = s;
                         //^
            }
        }""")
  }

  private fun checkType(type: SolType, @Language("Solidity") code: String) {
    val (refElement, data) = resolveInCode<SolExpression>(code)
    TestCase.assertEquals(type, refElement.type)
  }
}
