package pk.nust.cuas.gateway.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pk.nust.cuas.gateway.realtime.message.Payloads.TracksDelta;
import pk.nust.cuas.gateway.realtime.message.Payloads.TracksSnapshot;
import pk.nust.cuas.gateway.realtime.message.ServerMessages;
import pk.nust.cuas.gateway.realtime.view.AlertView;
import pk.nust.cuas.gateway.realtime.view.TrackView;

/** The gateway's live view and coalescing fan-out. */
class StreamHubTest {

    private final SessionRegistry registry = mock(SessionRegistry.class);
    private final StreamHub hub = new StreamHub(registry);

    @Test
    void trackChangesAreCoalescedLatestWinsIntoOneDelta() {
        GatewaySession session = mock(GatewaySession.class);
        when(session.nextSeq(Channel.TRACKS)).thenReturn(1L);
        when(registry.subscribersOf(Channel.TRACKS)).thenReturn(List.of(session));

        hub.onTrack(track("t1", 10.0));
        hub.onTrack(track("t1", 20.0));   // supersedes within the window
        hub.flush();

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(session).send(sent.capture());
        ServerMessages.Delta delta = (ServerMessages.Delta) sent.getValue();
        assertThat(delta.seq()).isEqualTo(1L);
        TracksDelta payload = (TracksDelta) delta.payload();
        assertThat(payload.changed()).singleElement()
                .satisfies(view -> assertThat(view.speedMs()).isEqualTo(20.0));
        assertThat(payload.removed()).isEmpty();
    }

    @Test
    void aLostTrackBecomesARemovalInTheNextDelta() {
        hub.onTrack(track("t1", 10.0));
        hub.flush();   // clears the pending create
        GatewaySession session = mock(GatewaySession.class);
        when(session.nextSeq(Channel.TRACKS)).thenReturn(2L);
        when(registry.subscribersOf(Channel.TRACKS)).thenReturn(List.of(session));

        hub.onTrackLost("t1");
        hub.flush();

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(session).send(sent.capture());
        TracksDelta payload = (TracksDelta) ((ServerMessages.Delta) sent.getValue()).payload();
        assertThat(payload.removed()).containsExactly("t1");
        assertThat(payload.changed()).isEmpty();
    }

    @Test
    void anAlertIsPushedImmediatelyWithoutCoalescing() {
        GatewaySession session = mock(GatewaySession.class);
        when(registry.subscribersOf(Channel.ALERTS)).thenReturn(List.of(session));

        hub.onAlert(new AlertView("a1", "ALT-1", "CRITICAL", "t1", "title", "summary", null));

        verify(session).send(org.mockito.ArgumentMatchers.any(ServerMessages.Alert.class));
    }

    @Test
    void theTracksSnapshotReflectsCurrentStateAndAlertsHaveNone() {
        hub.onTrack(track("t1", 10.0));
        hub.onTrack(track("t2", 15.0));

        TracksSnapshot snapshot = (TracksSnapshot) hub.snapshotPayload(Channel.TRACKS);
        assertThat(snapshot.tracks()).extracting(TrackView::trackId)
                .containsExactlyInAnyOrder("t1", "t2");
        assertThat(hub.snapshotPayload(Channel.ALERTS)).isNull();
    }

    private static TrackView track(String id, double speed) {
        return new TrackView(id, "sector-NE", "CONFIRMED", 33.6, 73.0, 120.0, speed, 90.0,
                "drone", 0.9, null, null, 3);
    }
}
