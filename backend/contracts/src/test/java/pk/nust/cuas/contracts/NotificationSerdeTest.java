package pk.nust.cuas.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Timestamp;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import pk.nust.cuas.notification.v1.DeliveryFailureReason;
import pk.nust.cuas.notification.v1.NotificationChannel;
import pk.nust.cuas.notification.v1.NotificationFailed;
import pk.nust.cuas.notification.v1.NotificationSent;
import pk.nust.cuas.notification.v1.NotificationTrigger;

/**
 * Serialization round-trip coverage for {@code notification.proto} (ARCHITECTURE.md sections
 * 5.3.5 and 4.1.9), plus the privacy property both events depend on: neither carries a channel
 * address or a message body onto a topic that audit mirrors and reporting indexes.
 */
class NotificationSerdeTest {

    /** Field-name fragments that would indicate a channel address or message body on the wire. */
    private static final List<String> FORBIDDEN_FRAGMENTS =
            List.of("email", "phone", "msisdn", "address", "body", "webhook_url", "recipient_name");

    @Test
    void sentRoundTripsWithAllFields() throws Exception {
        NotificationSent original = NotificationSent.newBuilder()
                .setNotificationId("01J8X4K3N2R5W1")
                .setChannel(NotificationChannel.NOTIFICATION_CHANNEL_EMAIL)
                .setTrigger(NotificationTrigger.NOTIFICATION_TRIGGER_ALERT_RAISED)
                .setDedupKey("usr-4411|01J8X4K3N2R5T1")
                .setRecipientUserId("usr-4411")
                .setAlertId("01J8X4K3N2R5T1").setTrackId("TRK-0473")
                .setProviderMessageId("ses-0000-aaaa").setAttempt(1).setLatencyMs(184)
                .setSentAt(Timestamp.newBuilder().setSeconds(1_784_562_305).build())
                .setSchemaVersion("1.0.0").setCorrelationId("corr-01")
                .build();

        assertThat(NotificationSent.parseFrom(original.toByteArray())).isEqualTo(original);
    }

    @Test
    void failedRoundTripsAndSeparatesARetryFromAGiveUp() throws Exception {
        NotificationFailed retryable = NotificationFailed.newBuilder()
                .setNotificationId("01J8X4K3N2R5W2")
                .setChannel(NotificationChannel.NOTIFICATION_CHANNEL_SMS)
                .setTrigger(NotificationTrigger.NOTIFICATION_TRIGGER_ALERT_ESCALATED)
                .setDedupKey("usr-4411|01J8X4K3N2R5T1")
                .setRecipientUserId("usr-4411").setAlertId("01J8X4K3N2R5T1")
                .setReason(DeliveryFailureReason.DELIVERY_FAILURE_REASON_PROVIDER_TIMEOUT)
                .setDetail("gateway did not respond within 5000 ms")
                .setAttempt(2).setWillRetry(true)
                .setNextAttemptAt(Timestamp.newBuilder().setSeconds(1_784_562_360).build())
                .setFailedAt(Timestamp.newBuilder().setSeconds(1_784_562_330).build())
                .setSchemaVersion("1.0.0").setCorrelationId("corr-01")
                .build();
        NotificationFailed terminal = retryable.toBuilder()
                .setReason(DeliveryFailureReason.DELIVERY_FAILURE_REASON_RECIPIENT_UNKNOWN)
                .setWillRetry(false)
                .clearNextAttemptAt()
                .build();

        assertThat(NotificationFailed.parseFrom(retryable.toByteArray())).isEqualTo(retryable);
        assertThat(NotificationFailed.parseFrom(terminal.toByteArray())).isEqualTo(terminal);
        // Retrying an unknown recipient can never succeed and would consume the per-channel
        // rate budget a genuinely retryable alert needs.
        assertThat(terminal.getWillRetry()).isFalse();
        assertThat(terminal.hasNextAttemptAt()).isFalse();
    }

    @Test
    void neitherEventCarriesAChannelAddressOrAMessageBody() {
        // Section 7.9: an email address or phone number on a topic audit mirrors and reporting
        // indexes would spread personal data across retention policies nobody chose for it.
        // The address lives in notifydb, keyed by recipient and channel.
        assertNoForbiddenField(NotificationSent.getDescriptor());
        assertNoForbiddenField(NotificationFailed.getDescriptor());
    }

    @Test
    void triggerVocabularyIsIndependentOfTopicNames() {
        // Per-user channel preferences are stored against this vocabulary. Topic names carry a
        // major version (section 5.5), so keying a stored preference on one would silently
        // invalidate every preference at a .v2 bump.
        assertThat(NotificationTrigger.values())
                .filteredOn(t -> t != NotificationTrigger.UNRECOGNIZED)
                .extracting(Enum::name)
                .allSatisfy(name -> assertThat(name).doesNotContain("_V1"));
    }

    @Test
    void allChannelTriggerAndFailureValuesPreserved() throws Exception {
        for (NotificationChannel channel : NotificationChannel.values()) {
            if (channel == NotificationChannel.UNRECOGNIZED) {
                continue;
            }
            NotificationSent original = NotificationSent.newBuilder().setChannel(channel).build();
            assertThat(NotificationSent.parseFrom(original.toByteArray()).getChannel())
                    .isEqualTo(channel);
        }
        for (NotificationTrigger trigger : NotificationTrigger.values()) {
            if (trigger == NotificationTrigger.UNRECOGNIZED) {
                continue;
            }
            NotificationSent original = NotificationSent.newBuilder().setTrigger(trigger).build();
            assertThat(NotificationSent.parseFrom(original.toByteArray()).getTrigger())
                    .isEqualTo(trigger);
        }
        for (DeliveryFailureReason reason : DeliveryFailureReason.values()) {
            if (reason == DeliveryFailureReason.UNRECOGNIZED) {
                continue;
            }
            NotificationFailed original =
                    NotificationFailed.newBuilder().setReason(reason).build();
            assertThat(NotificationFailed.parseFrom(original.toByteArray()).getReason())
                    .isEqualTo(reason);
        }
    }

    private static void assertNoForbiddenField(Descriptors.Descriptor descriptor) {
        assertThat(descriptor.getFields())
                .extracting(Descriptors.FieldDescriptor::getName)
                .allSatisfy(name -> assertThat(FORBIDDEN_FRAGMENTS)
                        .as("%s.%s must not carry a channel address or message body",
                                descriptor.getName(), name)
                        .noneMatch(name.toLowerCase(Locale.ROOT)::contains));
    }
}
