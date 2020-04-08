package me.serce.solidity.lang.completion

class SolStateVarCompletionTest : SolCompletionTestBase() {
  fun testStructMember() = checkCompletion(hashSetOf("owner1", "owner2"), """
        contract B {
            struct C {
                address owner1;
                address owner2;
            }

            function doit(C c) {
                c.ow/*caret*/;
            }
        }
  """)

  fun testStructMemberMultiple() = checkCompletion(hashSetOf("owner1", "owner2"), """
        contract B {
            struct C {
                int nway;
                string prop;
                address owner1;
                address owner2;
            }
            C c;

            function doit() {
                c.ow/*caret*/;
            }
        }
  """, strict = true)

  fun testContractMember() = checkCompletion(hashSetOf("owner1", "owner2"), """
        contract C {
            address public owner1;
            address public owner2;
        }
        contract B {
            function doit(C c) {
                c.ow/*caret*/;
            }
        }
  """, strict = true)

  fun testContractMemberInheritance() = checkCompletion(hashSetOf("owner1", "owner2"), """
        contract C {
            address public owner1;
            address public owner2;
        }
        contract D is C {}
        contract B {
            function doit(D c) {
                c.ow/*caret*/;
            }
        }
  """, strict = true)
}
