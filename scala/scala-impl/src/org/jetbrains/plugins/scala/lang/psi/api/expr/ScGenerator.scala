package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */
trait ScGenerator extends ScEnumerator with ScPatterned {
  def guard: ScGuard

  def rvalue: ScExpression


  def chooseToken: PsiElement
    = findFirstChildByType(ScalaTokenTypes.tCHOOSE)

  def valKeyword: Option[PsiElement] =
    Option(getNode.findChildByType(ScalaTokenTypes.kVAL)).map(_.getPsi)

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitGenerator(this)
  }
}