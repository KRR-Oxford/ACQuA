/*
 * Copyright 2021,2022 KRR Oxford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.ox.cs.acqua.implicits

import uk.ac.ox.cs.JRDFox.model.{
  BlankNode => OldBlankNode,
  Datatype => OldDatatype,
  Individual => OldIndividual,
  Literal => OldLiteral,
}
import tech.oxfordsemantic.jrdfox.logic.Datatype
import tech.oxfordsemantic.jrdfox.logic.expression.{
  BlankNode,
  IRI,
  Literal,
  Resource
}
import uk.ac.ox.cs.pagoda.query.{AnswerTuple,AnswerTuples}
import uk.ac.ox.cs.rsacomb.sparql.ConjunctiveQueryAnswers

/** Implicit wrapper around [[uk.ac.ox.cs.rsacomb.sparql.ConjunctiveQueryAnswers]]
  *
  * It implicitly converts a [[uk.ac.ox.cs.rsacomb.sparql.ConjunctiveQueryAnswers]]
  * into a [[uk.ac.ox.cs.pagoda.query.AnswerTuples]] to maintain
  * compatibility betweren RSAComb and PAGOdA.
  */
object RSACombAnswerTuples {

  implicit class RSACombAnswerTuples(
      val answers: ConjunctiveQueryAnswers
    ) extends AnswerTuples {

    /* Iterator simulated using an index over an [[IndexedSeq]]
     *
     * This might not be the best solution, but at least it offers
     * better flexibility than using the internal [[Seq]] iterator.
     * On top of this, indexed access is guaranteed to be efficient.
     */
    private var iter = answers.answers.map(_._2).toIndexedSeq
    private var idx: Int = 0

    /** Reset the iterator over the answers. */
    def reset(): Unit = idx = 0

    /** True if the iterator can provide more items. */
    def isValid: Boolean = idx < iter.length

    /** Get arity of answer variables. */
    def getArity: Int = answers.query.answer.length

    /** Get array of answer variable names */
    def getAnswerVariables: Array[String] =
      answers.query.answer.map(_.getName).toArray

    /** Advance iterator state */
    def moveNext(): Unit = idx += 1

    /** Get next [[uk.ac.ox.cs.pagoda.query.AnswerTuple]] from the iterator */
    def getTuple: AnswerTuple = iter(idx)

    /** Return true if the input tuple is part of this collection.
      *
      * @param tuple the answer to be checked.
      *
      * @note this operation is currently not supported.
      */
    def contains(tuple: AnswerTuple): Boolean = ???

    /** Skip one item in the iterator.
      *
      * @note that the semantic of this method is not clear to the
      * author and the description is just an assumption.
      */
    def remove(): Unit = moveNext()
  }

  /** Implicit convertion from RSAComb-style answers to [[uk.ac.ox.cs.pagoda.query.AnswerTuple]] */
  private implicit def asAnswerTuple(
    answer: Seq[Resource]
  ): AnswerTuple = new AnswerTuple(answer.map(res =>
    res match {
      case r: IRI => OldIndividual.create(r.getIRI)
      case r: BlankNode => OldBlankNode.create(r.getID)
      case r: Literal => OldLiteral.create(r.getLexicalForm,r.getDatatype)
    }
  ).toArray)

  /** Implicit convertion from [[tech.oxfordsemantic.jrdfox.logic.Datatype]] to [[uk.ac.ox.cs.JRDFox.model.Datatype]]
    *
    * @note this might not be 100% accurate since the two interfaces are
    * slightly different.
    */
  private implicit def asOldDatatype(
    datatype: Datatype
  ): OldDatatype = datatype match {
    case Datatype.BLANK_NODE => OldDatatype.BLANK_NODE
    case Datatype.IRI_REFERENCE => OldDatatype.IRI_REFERENCE
    case Datatype.RDF_PLAIN_LITERAL => OldDatatype.RDF_PLAIN_LITERAL
    case Datatype.RDFS_LITERAL => OldDatatype.RDFS_LITERAL
    case Datatype.XSD_ANY_URI => OldDatatype.XSD_ANY_URI
    case Datatype.XSD_BOOLEAN => OldDatatype.XSD_BOOLEAN
    case Datatype.XSD_BYTE => OldDatatype.XSD_BYTE
    case Datatype.XSD_DATE => OldDatatype.XSD_DATE
    case Datatype.XSD_DATE_TIME => OldDatatype.XSD_DATE_TIME
    case Datatype.XSD_DATE_TIME_STAMP => OldDatatype.XSD_DATE_TIME_STAMP
    case Datatype.XSD_DECIMAL => OldDatatype.XSD_DECIMAL
    case Datatype.XSD_DOUBLE => OldDatatype.XSD_DOUBLE
    case Datatype.XSD_DURATION => OldDatatype.XSD_DURATION
    case Datatype.XSD_FLOAT => OldDatatype.XSD_FLOAT
    case Datatype.XSD_G_DAY => OldDatatype.XSD_G_DAY
    case Datatype.XSD_G_MONTH => OldDatatype.XSD_G_MONTH
    case Datatype.XSD_G_MONTH_DAY => OldDatatype.XSD_G_MONTH_DAY
    case Datatype.XSD_G_YEAR => OldDatatype.XSD_G_YEAR
    case Datatype.XSD_G_YEAR_MONTH => OldDatatype.XSD_G_YEAR_MONTH
    case Datatype.XSD_INT => OldDatatype.XSD_INT
    case Datatype.XSD_INTEGER => OldDatatype.XSD_INTEGER
    case Datatype.XSD_LONG => OldDatatype.XSD_LONG
    case Datatype.XSD_NEGATIVE_INTEGER => OldDatatype.XSD_NEGATIVE_INTEGER
    case Datatype.XSD_NON_NEGATIVE_INTEGER => OldDatatype.XSD_NON_NEGATIVE_INTEGER
    case Datatype.XSD_NON_POSITIVE_INTEGER => OldDatatype.XSD_NON_POSITIVE_INTEGER
    case Datatype.XSD_POSITIVE_INTEGER => OldDatatype.XSD_POSITIVE_INTEGER
    case Datatype.XSD_SHORT => OldDatatype.XSD_SHORT
    case Datatype.XSD_STRING => OldDatatype.XSD_STRING
    case Datatype.XSD_TIME => OldDatatype.XSD_TIME
    case Datatype.XSD_UNSIGNED_BYTE => OldDatatype.XSD_UNSIGNED_BYTE
    case Datatype.XSD_UNSIGNED_INT => OldDatatype.XSD_UNSIGNED_INT
    case Datatype.XSD_UNSIGNED_LONG => OldDatatype.XSD_UNSIGNED_LONG
    case Datatype.XSD_UNSIGNED_SHORT => OldDatatype.XSD_UNSIGNED_SHORT
    case Datatype.XSD_DAY_TIME_DURATION => OldDatatype.XSD_DURATION
    case Datatype.XSD_YEAR_MONTH_DURATION => OldDatatype.XSD_DURATION
    case Datatype.INVALID_DATATYPE => OldDatatype.XSD_ANY_URI
  }
}
