package me.serce.solidity.lang.core.resolve

import me.serce.solidity.lang.psi.SolDotExpression
import org.intellij.lang.annotations.Language

class SolMemberAccessResolveTest : SolResolveTestBase() {
  fun testResolveStructMember() = checkByCode("""
      contract B {
          struct Prop {
              uint8 prop;
                   //x
          }

          Prop[] aa;

          function B() {
              aa[0].prop;
                   //^
          }
      }
  """)

  fun testResolveContractMember() = checkByCode("""
      contract C {
          int public prop;
                    //x
      }

      contract B {
          function B(C c) {
              c.prop;
              //^
          }
      }
  """)

  fun testResolveContractMemberWhenFunction() = checkByCode("""
      contract C {
          int public prop;
                    //x
                    
          function prop(uint value) public {
          
          }
      }

      contract B {
          function B(C c) {
              c.prop();
              //^
          }
      }
  """)

  fun testResolveContractParent() = checkByCode("""
      contract C {
          int public prop;
                    //x
      }

      contract K {
          int public noprop;
      }

      contract D is K, C {
          int public neigherprop;
      }

      contract B {
          function B(C c) {
              c.prop;
               //^
          }
      }
  """)

  fun testResolveInsideFunctionCall() = checkByCode("""
      contract C {
          bool public def;
                     //x 
      }

      contract test {
          function test(C c){
              require(c.def, "");
                       //^
          }
      }
  """)

  fun testResolveThis() = checkByCode("""
        contract B {
            function doit2() {
                    //x
            }


            function doit() {
                this.doit2();
                     //^
            }
        }
  """)

  fun testResolveFunctionUsingSuper() = checkByCode("""
        contract Parent1 {
        }

        contract Parent2 {
            function doSomething() {
                    //x
            }
        }

        contract B is Parent1, Parent2 {
            function doSomething() {
                super.doSomething();
                     //^
            }
        }
  """)

  fun testResolveContractProperty() = checkByCode("""
        contract A {
            function doit2() {
                    //x
            }
        }

        contract B {
            A a;

            function doit() {
                a.doit2();
                  //^
            }
        }
  """)

  fun testResolveUsingLibrary1() = checkByCode("""
        library Library {
            function something(bytes self, uint256 go) internal pure returns (uint256) {
                    //x
                return go;
            }
        }

        contract B {
            using Library for bytes;

            function doit(bytes value) {
                value.something(60);
                     //^
            }
        }
  """)

  fun testResolveUsingLibraryWithInheritance() = checkByCode("""
        library Library {
            function something(bytes self, uint256 go) internal pure returns (uint256) {
                    //x
                return go;
            }
        }

        contract Super {
            using Library for bytes;
        }

        contract B is Super {
            using Library for bytes;

            function doit(bytes value) {
                value.something(60);
                     //^
            }
        }
  """)

  fun testResolveUsingLibrary2() = checkByCode("""
        contract SomeContract {}
        
        contract ChildContract is SomeContract {
        
        }

        library Library {
            function something(SomeContract self, uint256 go) internal pure returns (uint256) {
                    //x
                return go;
            }
        }

        contract B {
            using Library for ChildContract;

            function doit(ChildContract value) {
                value.something(60);
                     //^
            }
        }
  """)

  fun testResolveUsingLibrary3() = checkByCode("""

        library Library {
            function findUpperBound(uint256[] storage array, uint256 element) internal view returns (uint256) {
                      //x
                return 0;
            }
        }

        contract B {
            using Library for uint256[];
            
            uint256[] private array;

            function doit(uint256  value) {
                array.findUpperBound(value);
                      //^
            }
        }
  """)

  fun testResolveUsingLibraryWithWildcard() = checkByCode("""
        library Library {
            function something(bytes self, uint256 go) internal pure returns (uint256) {
                    //x
                return go;
            }
        }

        contract B {
            using Library for *;

            function doit(bytes value) {
                value.something(60);
                     //^
            }
        }
  """)

  fun testResolveWithCast() = checkByCode("""
        contract A {
            function doit2() {
                    //x
            }
        }

        contract B {
            function doit(address some) {
                A(some).doit2();
                       //^
            }
        }
  """)

  fun testResolvePublicVar() = checkByCode("""
        contract A {
            uint public value;
                       //x
        }

        contract B {
            function doit(address some) {
                A(some).value();
                       //^
            }
        }
  """)

  fun testResolveTransfer() {
    checkIsResolved("""
        contract B {
            function doit(address some) {
                some.transfer(100);
                       //^
            }
        }
  """)
  }

  fun testResolveArrayPush() {
    checkIsResolved("""
        contract B {
            function doit(uint256[] storage array) {
                array.push(100);
                     //^
            }
        }
  """)
  }

  fun checkIsResolved(@Language("Solidity") code: String) {
    val (refElement, _) = resolveInCode<SolDotExpression>(code)
    assertNotNull(refElement.reference?.resolve())
  }
}
