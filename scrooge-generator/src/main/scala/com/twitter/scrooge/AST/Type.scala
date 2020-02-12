package com.twitter.scrooge.ast

sealed trait FunctionType extends TypeNode
case object Void extends FunctionType
case object OnewayVoid extends FunctionType
sealed trait FieldType extends FunctionType
sealed trait BaseType extends FieldType
case object TBool extends BaseType
case object TByte extends BaseType
case object TI16 extends BaseType
case object TI32 extends BaseType
case object TI64 extends BaseType
case object TDouble extends BaseType
case object TString extends BaseType
case object TBinary extends BaseType

/**
 * ReferenceType is generated by ThriftParser in the frontend and
 * resolved by TypeResolver. There will only ReferenceTypes after
 * resolution seen by the backend when self-reference structs,
 * mutually recursive structs, or references to further definitions
 * (structs/enums) are present in the Document.
 */
case class ReferenceType(id: Identifier) extends FieldType

sealed trait NamedType extends FieldType {
  def sid: SimpleID

  /** Filename of the containing file if the type is included from another file */
  def scopePrefix: Option[SimpleID]
}

case class StructType(struct: StructLike, scopePrefix: Option[SimpleID] = None) extends NamedType {
  val sid: SimpleID = struct.sid
  override def toString: String = "Struct(?)"
}

case class EnumType(enum: Enum, scopePrefix: Option[SimpleID] = None) extends NamedType {
  val sid: SimpleID = enum.sid
  override def toString: String = "Enum(?)"
}

sealed abstract class ContainerType(cppType: Option[String]) extends FieldType

case class MapType(keyType: FieldType, valueType: FieldType, cppType: Option[String])
    extends ContainerType(cppType) {

  override def toString: String = s"Map($keyType, $valueType)"
}

case class SetType(eltType: FieldType, cppType: Option[String]) extends ContainerType(cppType) {
  override def toString: String = s"Set($eltType)"
}

case class ListType(eltType: FieldType, cppType: Option[String]) extends ContainerType(cppType) {
  override def toString: String = s"List($eltType)"
}
