package org.jetbrains.plugins.scala.lang.psi.types.api

import java.util.concurrent.ConcurrentMap

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions.{TraversableExt, SeqExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType.substitutorCache
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
trait ParameterizedType extends ValueType {

  override implicit def projectContext: ProjectContext = designator.projectContext

  val designator: ScType
  val typeArguments: Seq[ScType]

  def substitutor: ScSubstitutor =
    substitutorCache.computeIfAbsent(this, _ => substitutorInner)

  protected def substitutorInner: ScSubstitutor

  override def updateSubtypes(substitutor: ScSubstitutor, variance: Variance)
                             (implicit visited: Set[ScType]): ScType = {

    val typeParameterVariances = designator.extractDesignated(expandAliases = false) match {
      case Some(n: ScTypeParametersOwner) => n.typeParameters.map(_.variance)
      case _                              => Seq.empty
    }
    val newDesignator = designator.recursiveUpdateImpl(substitutor, variance)
    val newTypeArgs = typeArguments.smartMapWithIndex {
      case (ta, i) =>
        val v = if (i < typeParameterVariances.length) typeParameterVariances(i) else Invariant
        ta.recursiveUpdateImpl(substitutor, v * variance)
    }

    if ((newDesignator eq designator) && (newTypeArgs eq typeArguments)) this
    else ParameterizedType(newDesignator, newTypeArgs)
  }

  override def typeDepth: Int = {
    val result = designator.typeDepth
    typeArguments.map(_.typeDepth) match {
      case Seq() => result //todo: shouldn't be possible
      case seq => result.max(seq.max + 1)
    }
  }

  override def isFinalType: Boolean =
    designator.isFinalType && typeArguments.filterBy[TypeParameterType].forall(_.isInvariant)


  //for name-based extractor
  final def isEmpty: Boolean = false
  final def get: ParameterizedType = this
  final def _1: ScType = designator
  final def _2: Seq[ScType] = typeArguments
}

object ParameterizedType {
  val substitutorCache: ConcurrentMap[ParameterizedType, ScSubstitutor] =
    ContainerUtil.newConcurrentMap[ParameterizedType, ScSubstitutor]()

  def apply(designator: ScType, typeArguments: Seq[ScType]): ValueType =
    designator.typeSystem.parameterizedType(designator, typeArguments)

  //designator and type arguments
  def unapply(p: ParameterizedType): ParameterizedType = p
}
