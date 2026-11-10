package io.github.azneotec.ticketing.catalogue.grpc.v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Shows the generated classes work end to end: a fake server and a client stub, in-process. */
class CatalogueServiceContractTest {

  private static final Instant STARTS_AT = Instant.parse("2027-06-01T18:00:00Z");

  private Server server;
  private ManagedChannel channel;

  @BeforeEach
  void start() throws Exception {
    String name = InProcessServerBuilder.generateName();
    server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(new FakeCatalogue())
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
  }

  @AfterEach
  void stop() {
    channel.shutdownNow();
    server.shutdownNow();
  }

  @Test
  void getEventReturnsTheEventWithItsSchedule() {
    var client = CatalogueServiceGrpc.newBlockingStub(channel);

    Event event =
        client.getEvent(GetEventRequest.newBuilder().setEventId("evt-1").build()).getEvent();

    assertThat(event.getTitle()).isEqualTo("Opening night");
    assertThat(event.getStatus()).isEqualTo(EventStatus.EVENT_STATUS_PUBLISHED);
    Schedule schedule = event.getSchedule();
    Instant startsAt = Instant.ofEpochSecond(schedule.getStartsAt().getSeconds());
    assertThat(startsAt.atZone(ZoneId.of(schedule.getZoneId())).getHour()).isEqualTo(19);
  }

  @Test
  void listEventsReturnsAPageAndTheNextPageToken() {
    var client = CatalogueServiceGrpc.newBlockingStub(channel);

    ListEventsResponse page = client.listEvents(ListEventsRequest.newBuilder().build());

    assertThat(page.getEventsList()).hasSize(1);
    assertThat(page.getNextPageToken()).isEqualTo("page-2");
  }

  @Test
  void unknownFieldsAreSkippedByOldReaders() throws Exception {
    // A newer writer sends field 99, which this (older) message does not know.
    byte[] newer =
        GetEventRequest.newBuilder()
            .setEventId("evt-1")
            .setUnknownFields(
                com.google.protobuf.UnknownFieldSet.newBuilder()
                    .addField(
                        99,
                        com.google.protobuf.UnknownFieldSet.Field.newBuilder().addVarint(7).build())
                    .build())
            .build()
            .toByteArray();

    assertThat(GetEventRequest.parseFrom(newer).getEventId()).isEqualTo("evt-1");
  }

  private static Event sampleEvent() {
    return Event.newBuilder()
        .setEventId("evt-1")
        .setTitle("Opening night")
        .setVenue(VenueSummary.newBuilder().setVenueId("ven-1").setName("Hall").setCity("London"))
        .setSchedule(
            Schedule.newBuilder()
                .setStartsAt(Timestamp.newBuilder().setSeconds(STARTS_AT.getEpochSecond()))
                .setZoneId("Europe/London"))
        .setStatus(EventStatus.EVENT_STATUS_PUBLISHED)
        .build();
  }

  private static final class FakeCatalogue extends CatalogueServiceGrpc.CatalogueServiceImplBase {
    @Override
    public void getEvent(GetEventRequest request, StreamObserver<GetEventResponse> response) {
      response.onNext(GetEventResponse.newBuilder().setEvent(sampleEvent()).build());
      response.onCompleted();
    }

    @Override
    public void listEvents(ListEventsRequest request, StreamObserver<ListEventsResponse> response) {
      response.onNext(
          ListEventsResponse.newBuilder()
              .addEvents(sampleEvent())
              .setNextPageToken("page-2")
              .build());
      response.onCompleted();
    }
  }
}
