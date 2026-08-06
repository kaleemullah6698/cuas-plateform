package pk.nust.cuas.notification.application.port;

import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.model.DeliveryResult;

/**
 * Delivers a notification over one channel (ARCHITECTURE.md section 4.1.9). The SPI that decouples the
 * dispatch pipeline from how a message actually leaves the platform.
 *
 * <p>One implementation per {@link Channel}. An implementation reports its outcome as a
 * {@link DeliveryResult} rather than throwing, so a provider failure is a first-class value the
 * pipeline routes through retry — a channel going down is an expected operational event, not a fault
 * that should abort processing.
 */
public interface ChannelProvider {

    /** @return the channel this provider delivers on */
    Channel channel();

    /**
     * Attempts to deliver.
     *
     * @param attempt what to deliver and where
     * @return the outcome — sent, or failed for a reason
     */
    DeliveryResult deliver(DeliveryAttempt attempt);
}
