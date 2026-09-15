package io.github.willywonka644.fintracker.serialization

import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}

object LocalDateSetSerializer : KSerializer<Set<LocalDate>> {
    private val delegate = SetSerializer(LocalDateSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: Set<LocalDate>) =
        delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): Set<LocalDate> =
        delegate.deserialize(decoder)
}

/**
 * Shared Json instance for all persistence (repositories, backup/restore).
 * - ignoreUnknownKeys: handles forward-compat when new fields are added
 * - explicitNulls = false: omits null fields from output (cleaner JSON)
 * - coerceInputValues: allows null JSON values for fields with defaults (backward-compat reads)
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
