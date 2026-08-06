package pk.nust.cuas.notification.infrastructure.channel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import pk.nust.cuas.notification.application.port.ChannelProvider;
import pk.nust.cuas.notification.application.port.DeliveryAttempt;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.DeliveryFailureReason;
import pk.nust.cuas.notification.domain.model.DeliveryResult;

/**
 * Delivers to an operator-configured HTTP webhook — the one channel with a real external endpoint in
 * the dev stack, and so the one that exercises the retry path when an endpoint is down or rejecting.
 *
 * <p>The payload carries the notification's context and one-line subject, never a message body: the
 * full content lives on the source topic, and the address itself (potentially a third-party URL) is
 * outside the platform trust boundary. HTTP outcomes map to failure reasons by their retry semantics:
 * a 5xx or a timeout is worth retrying, a 4xx is the endpoint's terminal rejection.
 */
@Component
public final class WebhookChannelProvider implements ChannelProvider {

    private final RestClient http;

    public WebhookChannelProvider(RestClient notificationWebhookClient) {
        this.http = notificationWebhookClient;
    }

    @Override
    public Channel channel() {
        return Channel.WEBHOOK;
    }

    @Override
    public DeliveryResult deliver(DeliveryAttempt attempt) {
        long startNanos = System.nanoTime();
        try {
            http.post()
                    .uri(attempt.address())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(attempt))
                    .retrieve()
                    .toBodilessEntity();
            return new DeliveryResult.Sent(Optional.empty(), elapsedMs(startNanos));
        } catch (HttpClientErrorException e) {
            DeliveryFailureReason reason = switch (e.getStatusCode().value()) {
                case 404, 410 -> DeliveryFailureReason.RECIPIENT_UNKNOWN;
                case 429 -> DeliveryFailureReason.RATE_LIMITED;
                default -> DeliveryFailureReason.REJECTED;
            };
            return new DeliveryResult.Failed(reason, e.getStatusCode().value() + " " + e.getStatusText());
        } catch (HttpServerErrorException e) {
            return new DeliveryResult.Failed(DeliveryFailureReason.PROVIDER_ERROR,
                    e.getStatusCode().value() + " " + e.getStatusText());
        } catch (ResourceAccessException e) {
            return new DeliveryResult.Failed(DeliveryFailureReason.PROVIDER_TIMEOUT, "endpoint unreachable");
        }
    }

    private static Map<String, Object> payload(DeliveryAttempt a) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("notificationId", a.notificationId().toString());
        body.put("trigger", a.trigger().name());
        body.put("urgency", a.urgency().name());
        body.put("subject", a.subject());
        a.refs().alertId().ifPresent(id -> body.put("alertId", id.toString()));
        a.refs().incidentId().ifPresent(id -> body.put("incidentId", id.toString()));
        a.refs().trackId().ifPresent(id -> body.put("trackId", id.toString()));
        body.put("correlationId", a.correlationId());
        return body;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
