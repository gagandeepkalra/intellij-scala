package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiPolyVariantReference, PsiReference, ResolveResult}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses

trait ScEnumerator extends ScalaPsiElement with PsiPolyVariantReference {
  def forStatement: Option[ScForStatement] = this.parentOfType(classOf[ScForStatement])

  def analog: Option[ScEnumerator.Analog] = forStatement flatMap {
    _.getDesugaredEnumeratorAnalog(this)
  }

  // the token that marks the enumerator (<-, =, if)
  def enumeratorToken: PsiElement

  override def getReference: PsiReference = this

  override def getElement: PsiElement = this

  override def getRangeInElement: TextRange = enumeratorToken.getTextRangeInParent

  private def mapDesugaredRef[R](f: PsiPolyVariantReference => R): Option[R] =
    analog.flatMap { _.callExpr }.map { ref => f(ref) }


  override def resolve(): PsiElement = mapDesugaredRef { _.resolve() }.orNull

  override def getCanonicalText: String = mapDesugaredRef { _.getCanonicalText }.orNull

  override def handleElementRename(newElementName: String): PsiElement =
    throw new IncorrectOperationException("Can not rename for enumerator")

  override def bindToElement(element: PsiElement): PsiElement =
    mapDesugaredRef { _.bindToElement(element) }.getOrElse { throw new IncorrectOperationException("Enumerator analog doesn't exist") }

  override def isReferenceTo(element: PsiElement): Boolean =
    mapDesugaredRef { _.isReferenceTo(element) }.getOrElse { false }

  override def isSoft: Boolean = true

  override def multiResolve(incompleteCode: Boolean): Array[ResolveResult] =
    mapDesugaredRef { _.multiResolve(incompleteCode) }.getOrElse { Array.empty[ResolveResult] }
}

object ScEnumerator {
  /*
    Analog maps enumerators to their desugared counterparts (which are method calls). For example:

      for { i <- List(1); i2 = i if i2 > 0; i3 = i2; i4 <- List(i3) } yield i4
            |----g1----|  |-d2-| |--if3--|  |-d4--|  |-----g5-----|
    maps to

      List(1).map { i => val i2 = i; (i, i2) }.withFilter { case (i, i2) => i2 > 0 }.flatMap { case (i, i2) => val i3 = i2; List(i3).map(i4 => i4) }
      |-----------------------------------g1:analogMethodCall--------------------------------------------------------------------------------------|
      |-----------------------g1:callExpr--------------------------------------------------|                   |-----g1:content------------------|

      |---------d2:analogMethodCall----------|
      |---------|        |----d2:content---|
      d2:callExpr

      |----------------------------if3:analogMethodCall----------------------------|
      |---------if3:callExpr----------------------------|                   |----|
                                                                          if3:content
                                                                                                                            |-----g5:analogMC----|

    Note that d4 does not have an analogMethodCall
   */

  case class Analog(analogMethodCall: ScMethodCall) {
    def callExpr: Option[ScReferenceExpression] =
      Option(analogMethodCall.getInvokedExpr).collect { case refExpr: ScReferenceExpression => refExpr }

    def content: Option[ScExpression] = {
      analogMethodCall
        .getLastChild
        .getLastChild
        .asInstanceOf[ScBlockExpr]
        .findLastChildByType[ScCaseClauses](ScalaElementType.CASE_CLAUSES)
        .getLastChild
        .lastChild collect { case block: ScBlock => block}
    }
  }
}