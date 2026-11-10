package io.github.azneotec.ticketing.catalogue.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;

/** Shows the generated class works and how Avro resolves writer vs reader schemas. */
class EventPublishedContractTest {

  private static final Instant STARTS_AT = Instant.parse("2027-06-01T18:00:00Z");

  @Test
  void roundTripsThroughAvroBinary() throws Exception {
    EventPublished event =
        EventPublished.newBuilder()
            .setEventId("evt-1")
            .setTitle("Opening night")
            .setVenueId("ven-1")
            .setStartsAt(STARTS_AT)
            .setZoneId("Europe/London")
            .setStatus(EventStatus.PUBLISHED)
            .build();

    ByteBuffer bytes = event.toByteBuffer();
    EventPublished read = EventPublished.fromByteBuffer(bytes);

    assertThat(read).isEqualTo(event);
    assertThat(read.getStartsAt().atZone(ZoneId.of(read.getZoneId())).getHour()).isEqualTo(19);
  }

  @Test
  void unknownEnumSymbolFromANewerWriterBecomesTheDefault() throws Exception {
    // A newer producer added the symbol POSTPONED, which this consumer's schema does not have.
    Schema newerStatus =
        SchemaBuilder.enumeration("EventStatus")
            .namespace("io.github.azneotec.ticketing.catalogue.v1")
            .defaultSymbol("UNKNOWN")
            .symbols("UNKNOWN", "PUBLISHED", "CANCELLED", "POSTPONED");
    Schema writer = withStatus(newerStatus);
    GenericRecord written = new GenericData.Record(writer);
    written.put("eventId", "evt-1");
    written.put("title", "Opening night");
    written.put("venueId", "ven-1");
    written.put("startsAt", STARTS_AT.toEpochMilli());
    written.put("zoneId", "Europe/London");
    written.put("status", new GenericData.EnumSymbol(newerStatus, "POSTPONED"));

    var out = new java.io.ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
    new GenericDatumWriter<GenericRecord>(writer).write(written, encoder);
    encoder.flush();

    // Schema resolution: decode with the writer's schema, map into the reader's (ours).
    var reader = new SpecificDatumReader<EventPublished>(writer, EventPublished.getClassSchema());
    EventPublished read =
        reader.read(null, DecoderFactory.get().binaryDecoder(out.toByteArray(), null));

    assertThat(read.getStatus()).isEqualTo(EventStatus.UNKNOWN);
  }

  /** The EventPublished schema with a different status enum. */
  private static Schema withStatus(Schema status) {
    return SchemaBuilder.record("EventPublished")
        .namespace("io.github.azneotec.ticketing.catalogue.v1")
        .fields()
        .requiredString("eventId")
        .requiredString("title")
        .requiredString("venueId")
        .name("startsAt")
        .type(
            org.apache.avro.LogicalTypes.timestampMillis()
                .addToSchema(Schema.create(Schema.Type.LONG)))
        .noDefault()
        .requiredString("zoneId")
        .name("status")
        .type(status)
        .noDefault()
        .endRecord();
  }
}
