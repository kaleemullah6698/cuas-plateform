package pk.nust.cuas.notification.domain.model;

import java.util.Optional;

/**
 * The outcome of one delivery attempt by a channel provider — sent, or failed for a reason.
 *
 * <p>Sealed because a provider must report exactly one of these two, and pattern matching over it is
 * exhaustive: a new outcome cannot be added without every handler being made to consider it.
 */
public sealed interface DeliveryResult {

    /**
     * The provider accepted the message.
     *
     * @param providerMessageId the provider's own handle for the message, when it returns one
     * @param latencyMs         how long the attempt took
     */
    record Sent(Optional<String> providerMessageId, long latencyMs) implements DeliveryResult {
        public Sent {
            if (providerMessageId == null) {
                throw new IllegalArgumentException("providerMessageId must not be null; use empty()");
            }
        }
    }

    /**
     * The attempt failed.
     *
     * @param reason why, and whether it is worth retrying
     * @param detail provider-reported detail for an operator; never the message body
     */
    record Failed(DeliveryFailureReason reason, String detail) implements DeliveryResult {
        public Failed {
            if (reason == null) {
                throw new IllegalArgumentException("reason is required");
            }
            detail = detail == null ? "" : detail;
        }
    }
}
