package me.serce.solidity.lang.completion

class SolFunctionsCompletionTest : SolCompletionTestBase() {

  fun testFuncNameIfConditionPrimExpression() {
    checkCompletion(
      hashSetOf("f_1", "f_2"), """
    contract FunctionHolder {
      function f_1() {}
      function f_2() {}
    }

    contract A is FunctionHolder {
      function example() {
        if(/*caret*/)
      }
    }
  """
    )
  }

  fun testFuncNameIfConditionFunctionCall() {
    checkCompletion(
      hashSetOf("f_1", "f_2"), """
        contract FunctionHolder {
          function f_1() {}
          function f_2() {}
        }
    
        contract A is FunctionHolder {
          function example() {
            if(f_/*caret*/())
          }
        }
      """
    )
  }

  fun testFuncNameWhileCond() {
    checkCompletion(
      hashSetOf("f_1", "f_2"), """
        contract FunctionHolder {
          function f_1() {}
          function f_2() {}
        }
    
        contract A is FunctionHolder {
          function example() {
            while(f_/*caret*/)
          }
        }
      """
    )
  }

  fun testFuncNameInheritance() {
    val result = checkCompletion(
      hashSetOf("f_1", "f_2"), """
        interface Interface {
          function f_1();
        }   
           
        contract FunctionHolder is Interface {
          function f_1() {}
          function f_2() {}
        }
        
        contract FunctionHolder2 is Interface {
        
        }
    
        contract A is FunctionHolder, FunctionHolder2 {
          function example() {
            return /*caret*/
          }
        }
      """
    )
    assertEquals("should be only one suggestion", result.filter { it == "f_1" }.size, 1)
  }

  fun testFuncNameReturn() {
    checkCompletion(
      hashSetOf("f_1", "f_2"), """
        contract FunctionHolder {
          function f_1() {}
          function f_2() {}
        }
    
        contract A is FunctionHolder {
          function example() {
            return /*caret*/
          }
        }
      """
    )
  }

  fun testFuncArgumentsTest() {
    checkCompletion(
      hashSetOf("f_1", "f_2"), """
            contract FunctionHolder {
              function f_1() {}
              function f_2() {}
            }

            contract A is FunctionHolder {
              function example(uint amount, uint balance) {
                f_1(/*caret*/);
              }
            }
          """, strict = false
    )
    checkCompletion(
      hashSetOf("f_1", "f_2"), """
            contract FunctionHolder {
              function f_1() {}
              function f_2() {}
            }

            contract A is FunctionHolder {
              function example(uint amount, uint balance) {
                f_1(amount, /*caret*/);
              }
            }
          """, strict = false
    )
    checkCompletion(
      hashSetOf("f_1", "f_2"), """
            contract FunctionHolder {
              function f_1() {}
              function f_2() {}
            }

            contract A is FunctionHolder {
              function example(uint amount, uint balance) {
                f_1(/*caret*/, amount);
              }
            }
          """
    )

    checkCompletion(
      hashSetOf("param1", "param2"), """
            contract A {
              function example(uint param1, uint param2) {
                f_1(/*caret*/, amount);
              }
            }
          """
    )

    checkCompletion(
      hashSetOf("param1", "param2"), """
            contract A {
              function example(uint param1, uint param2) {
                f_1(amount, /*caret*/);
              }
            }
          """
    )
  }

  fun testFuncArgumentsTestIncompleteStatement() {
    checkCompletion(
      hashSetOf("param1", "param2"), """
        contract A {
          function example(uint param1, uint param2) {
            f_1(/*caret*/)
          }
        }
      """
    )
  }

  fun testFunIncomplete() {
    checkCompletion(
      hashSetOf(), """
        contract B {
            function/*caret*/{}
        }
      """, strict = true
    )
  }
}
