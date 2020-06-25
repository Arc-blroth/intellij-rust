/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.FileTree
import org.rust.RsTestBase
import org.rust.fileTree

class ExtractInlineModuleIntentionTest : RsTestBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/"

    fun `test valid extract inline module`() = doTest(
        fileTree {
            rust("main.rs", """
                mod /*caret*/foo {
                    // function
                    fn a() {}
                }

                fn main() {}
            """)
        },
        fileTree {
            rust("main.rs", """
                mod foo;

                fn main() {}
            """)
            rust("foo.rs", """
                // function
                fn a() {}
            """)
        }
    )

    fun `test extract non-root module`() = doTest(
        fileTree {
            rust("main.rs", """
                mod foo;

                fn main() {}
            """)
            rust("foo.rs", """
                mod /*caret*/bar {
                    fn baz() {}
                }
            """)
        },
        fileTree {
            rust("main.rs", """
                mod foo;

                fn main() {}
            """)
            rust("foo.rs", """
                mod bar;
            """)
            dir("foo") {
                rust("bar.rs", """
                    fn baz() {}
                """)
            }
        }
    )

    fun `test keep existing file content`() = doTest(
        fileTree {
            rust("main.rs", """
                mod /*caret*/foo {
                    fn b() {}
                }

                fn main() {}
            """)
            rust("foo.rs", """
                fn a() {}
            """)
        },
        fileTree {
            rust("main.rs", """
                mod foo;

                fn main() {}
            """)
            rust("foo.rs", """
                fn a() {}

                fn b() {}
            """)
        }
    )

    fun `test extracting module preserves attributes and visibility`() = ExtractInlineModuleIntention.Testmarks.copyAttrs.checkHit {
        doTest(
            fileTree {
                rust("main.rs", """
                #[cfg(test)]
                pub(in super) mod /*caret*/tests {
                    #[test]
                    fn foo() {}
                }
            """)
            },
            fileTree {
                rust("main.rs", """
                #[cfg(test)]
                pub(in super) mod tests;
            """)
                rust("tests.rs", """
                #[test]
                fn foo() {}
            """)
            }
        )
    }

    fun `test invalid extract inline module`() {
        doTest(fileTree {
            rust("main.rs", """
                mod foo {
                    // function
                    fn a() {}
                }

                fn /*caret*/main() {}
            """)
        }, fileTree {
            rust("main.rs", """
                mod foo {
                    // function
                    fn a() {}
                }

                fn main() {}
            """)
        })
    }

    private fun doTest(before: FileTree, after: FileTree) {
        val testProject = before.create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        myFixture.launchAction(ExtractInlineModuleIntention())
        after.assertEquals(myFixture.findFileInTempDir("."))
    }
}
