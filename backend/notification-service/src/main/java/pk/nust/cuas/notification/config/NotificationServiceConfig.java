package pk.nust.cuas.notification.config;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import pk.nust.cuas.commons.id.Uuidv7Generator;
import pk.nust.cuas.notification.application.port.ChannelProvider;
import pk.nust.cuas.notification.domain.model.Channel;
import pk.nust.cuas.notification.domain.service.DeliverabilityPolicy;
import pk.nust.cuas.notification.domain.service.RetryPolicy;
import pk.nust.cuas.notification.infrastructure.channel.SimulatedChannelProvider;

/**
 * Wires the framework-free domain policies, the channel providers, and the webhook HTTP client.
 *
 * <p>The policies carry no Spring annotations — they compile without Spring on the classpath, the
 * layering test CLAUDE.md section 2 sets for {@code domain/} — and become beans here. Three channels
 * (WebSocket, email, SMS) are simulated in the dev stack because the platform provisions no relay for
 * them; the webhook channel is real HTTP. Each is an equal {@link ChannelProvider}, so promoting a
 * simulated one to a real provider is a bean swap and nothing more (section 6.4's stance, applied to
 * delivery).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Uuidv7Generator uuidv7Generator() {
        return new Uuidv7Generator(new SecureRandom());
    }

    @Bean
    public DeliverabilityPolicy deliverabilityPolicy() {
        return new DeliverabilityPolicy();
    }

    @Bean
    public RetryPolicy retryPolicy(NotificationProperties properties) {
        NotificationProperties.Retry retry = properties.retry();
        return new RetryPolicy(retry.maxAttempts(), retry.baseBackoff(), retry.maxBackoff());
    }

    @Bean
    public ChannelProvider websocketChannelProvider() {
        return new SimulatedChannelProvider(Channel.WEBSOCKET);
    }

    @Bean
    public ChannelProvider emailChannelProvider() {
        return new SimulatedChannelProvider(Channel.EMAIL);
    }

    @Bean
    public ChannelProvider smsChannelProvider() {
        return new SimulatedChannelProvider(Channel.SMS);
    }

    /** The HTTP client for the webhook channel, with the configured connect and read timeouts. */
    @Bean
    public RestClient notificationWebhookClient(NotificationProperties properties) {
        NotificationProperties.Webhook webhook = properties.webhook();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(webhook.connectTimeout());
        factory.setReadTimeout(webhook.readTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }
}
