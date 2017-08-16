package org.jetbrains.plugins.scala
package codeInspection
package typeAnnotation

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScTypedStmt, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.settings.annotations._
import org.jetbrains.plugins.scala.util._

/**
  * Pavel Fatin
  */
class TypeAnnotationInspection extends AbstractInspection {
  import TypeAnnotationInspection._

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case value: ScPatternDefinition if value.isSimple && !value.hasExplicitType =>
      inspect(value, value.bindings.head, value.expr)
    case variable: ScVariableDefinition if variable.isSimple && !variable.hasExplicitType =>
      inspect(variable, variable.bindings.head, variable.expr)
    case method: ScFunctionDefinition if method.hasAssign && !method.hasExplicitType && !method.isSecondaryConstructor =>
      inspect(method, method.nameId, method.body)
    case (parameter: ScParameter) && Parent(Parent(Parent(_: ScFunctionExpr))) if parameter.typeElement.isEmpty =>
      inspect(parameter, parameter.nameId, implementation = None)
    case (underscore: ScUnderscoreSection) && Parent(parent) if !parent.isInstanceOf[ScTypedStmt] =>
      inspect(underscore, underscore, implementation = None)
  }
}

object TypeAnnotationInspection {
  private[typeAnnotation] val Description = "Explicit type annotation required (according to Code Style settings)"

  private def inspect(element: ScalaPsiElement,
                      anchor: PsiElement,
                      implementation: Option[ScExpression])
                     (implicit holder: ProblemsHolder): Unit = {

    val declaration = Declaration(element)
    val location = Location(element)

    if (ScalaTypeAnnotationSettings(element.getProject).isTypeAnnotationRequiredFor(
      declaration, location, implementation.map(Implementation.Expression(_)))) {

      // TODO Create the general-purpose inspection
      val canBePrivate = declaration.entity match {
        case Entity.Method | Entity.Value | Entity.Variable if !location.isInLocalScope =>
          declaration.visibility != Visibility.Private
        case _ => false
      }

      val fixes =
        canBePrivate.seq(new MakePrivateQuickFix(element.asInstanceOf[ScModifierListOwner])) ++
          Seq(new AddTypeAnnotationQuickFix(anchor), new ModifyCodeStyleQuickFix(), new LearnWhyQuickFix())

      holder.registerProblem(anchor, Description, fixes: _*)
    }
  }

  private class MakePrivateQuickFix(element: ScModifierListOwner) extends AbstractFixOnPsiElement("Make private", element) {
    override def doApplyFix(project: Project): Unit = {
      element.setModifierProperty("private", value = true)
    }
  }

  private class LearnWhyQuickFix extends LocalQuickFixBase("Learn Why...") {
    override def applyFix(project: Project, problemDescriptor: ProblemDescriptor): Unit =
      DesktopUtils.browse("http://blog.jetbrains.com/scala/2016/10/05/beyond-code-style/")
  }

  private class ModifyCodeStyleQuickFix extends LocalQuickFixBase("Modify Code Style...") {
    override def applyFix(project: Project, problemDescriptor: ProblemDescriptor): Unit =
      TypeAnnotationUtil.showTypeAnnotationsSettings(project)
  }
}
