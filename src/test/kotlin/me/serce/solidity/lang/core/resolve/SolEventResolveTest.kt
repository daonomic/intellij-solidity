package me.serce.solidity.lang.core.resolve

import me.serce.solidity.lang.psi.SolCallExpression
import me.serce.solidity.lang.psi.SolNamedElement

class SolEventResolveTest : SolResolveTestBase() {
  fun testEventWithNoArguments() = checkByCode("""
        contract B {
            event Closed();
                    //x
            function close() {
                Closed();
                //^
            }
        }
  """)

  fun testEventParent() = checkByCode("""
        contract A {
            event Closed();
                    //x
        }

        contract B is A {
            function close() {
                Closed();
                //^
            }
        }
  """)

  fun testEventWithParemeters() = checkByCode("""
        contract B {
            event Refunded(int a, uint256 b);
                    //x

            function close() {
                Refunded(1, 2);
                //^
            }
        }
  """)

  override fun checkByCode(code: String) {
    checkByCodeInternal<SolCallExpression, SolNamedElement>(code)
  }
}
