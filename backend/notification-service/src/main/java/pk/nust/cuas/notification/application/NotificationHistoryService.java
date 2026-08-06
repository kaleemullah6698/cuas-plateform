package pk.nust.cuas.notification.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pk.nust.cuas.notification.domain.model.Notification;
import pk.nust.cuas.notification.domain.port.NotificationRepository;

/**
 * Reads a user's notification history (ARCHITECTURE.md section 4.1.9, {@code GET /notifications/history}).
 */
@Service
public class NotificationHistoryService {

    private final NotificationRepository notifications;

    public NotificationHistoryService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public List<Notification> history(UUID userId, int limit) {
        return notifications.history(userId, limit);
    }
}
