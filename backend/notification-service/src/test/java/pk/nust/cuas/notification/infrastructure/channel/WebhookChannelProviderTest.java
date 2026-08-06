package pk.nust.cuas.notification.infrastructure.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pk.nust.cuas.notification.application.port.DeliveryAttempt;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.DeliveryFailureReason;
import pk.nust.cuas.notification.domain.model.DeliveryResult;
import pk.nust.cuas.notification.domain.model.NotificationTrigger;
import pk.nust.cuas.notification.domain.model.SubjectRef;
import pk.nust.cuas.notification.domain.model.Urgency;

/** How {@link WebhookChannelProvider} maps HTTP outcomes onto retryable and terminal failure reasons. */
class WebhookChannelProviderTest {

    private static final String URL = "https://hook.example.mil/notify";

    @Test
    void aSuccessfulPostIsASend() {
        assertThat(deliverAgainst(withSuccess())).isInstanceOf(DeliveryResult.Sent.class);
    }

    @Test
    void aServerErrorIsRetryable() {
        DeliveryResult result = deliverAgainst(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(result).isInstanceOfSatisfying(DeliveryResult.Failed.class,
                f -> assertThat(f.reason()).isEqualTo(DeliveryFailureReason.PROVIDER_ERROR));
    }

    @Test
    void aNotFoundIsATerminalUnknownRecipient() {
        DeliveryResult result = deliverAgainst(withStatus(HttpStatus.NOT_FOUND));

        assertThat(result).isInstanceOfSatisfying(DeliveryResult.Failed.class,
                f -> assertThat(f.reason()).isEqualTo(DeliveryFailureReason.RECIPIENT_UNKNOWN));
    }

    @Test
    void tooManyRequestsIsRetryableAsRateLimited() {
        DeliveryResult result = deliverAgainst(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(result).isInstanceOfSatisfying(DeliveryResult.Failed.class,
                f -> assertThat(f.reason()).isEqualTo(DeliveryFailureReason.RATE_LIMITED));
    }

    @Test
    void anotherClientErrorIsATerminalRejection() {
        DeliveryResult result = deliverAgainst(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(result).isInstanceOfSatisfying(DeliveryResult.Failed.class,
                f -> assertThat(f.reason()).isEqualTo(DeliveryFailureReason.REJECTED));
    }

    private static DeliveryResult deliverAgainst(
            org.springframework.test.web.client.response.DefaultResponseCreator response) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andRespond(response);
        WebhookChannelProvider provider = new WebhookChannelProvider(builder.build());

        return provider.deliver(new DeliveryAttempt(UUID.randomUUID(), Channel.WEBHOOK, URL,
                NotificationTrigger.ALERT_RAISED, Urgency.HIGH, "subject", SubjectRef.none(), "corr-1"));
    }
}
