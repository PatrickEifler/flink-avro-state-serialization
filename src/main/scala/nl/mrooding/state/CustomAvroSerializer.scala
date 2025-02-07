package nl.mrooding.state

import nl.mrooding.data.AvroGenericRecordWriter
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter, GenericRecord}
import org.apache.avro.io.{BinaryEncoder, DecoderFactory, EncoderFactory}
import org.apache.flink.api.common.typeutils._
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import org.apache.commons.io.output.ByteArrayOutputStream

trait CustomAvroSerializer[T <: AvroGenericRecordWriter] extends TypeSerializer[T] with Serializable {
  def stateSchema: Option[Schema]
  def getCurrentSchema: Schema

  def fromGenericRecord(genericRecord: GenericRecord): T

  override def serialize(instance: T, target: DataOutputView): Unit = {
    val genericRecord = instance.toGenericRecord

    val outputStream: ByteArrayOutputStream = new ByteArrayOutputStream()
    val avroEncoder: BinaryEncoder = EncoderFactory.get().binaryEncoder(outputStream, null)

    new GenericDatumWriter[GenericRecord](genericRecord.getSchema).write(genericRecord, avroEncoder)
    avroEncoder.flush()

    val result = outputStream.toByteArray

    target.writeInt(result.length)
    target.write(result)
  }

  override def deserialize(source: DataInputView): T = {
    val blobSize = source.readInt()
    val blob = new Array[Byte](blobSize)
    source.read(blob)

    val decoder = DecoderFactory.get().binaryDecoder(blob, null)

    val reader = stateSchema match {
      case Some(previousSchema) => new GenericDatumReader[GenericRecord](previousSchema, getCurrentSchema)
      case None    => new GenericDatumReader[GenericRecord](getCurrentSchema)
    }
    val genericRecord = reader.read(null, decoder)

    fromGenericRecord(genericRecord)
  }

  /*
    Default functions required for TypeSerializer
   */

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case _ => false
    }
  }

  override def hashCode(): Int = 1

  override def isImmutableType: Boolean = false

  override def getLength: Int = -1

  override def copy(from: T): T = from

  override def copy(from: T, reuse: T): T = copy(from)

  override def copy(source: DataInputView, target: DataOutputView): Unit = serialize(deserialize(source), target)

  override def deserialize(reuse: T, source: DataInputView): T = deserialize(source)
}
