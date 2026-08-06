package pk.nust.cuas.notification.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.nust.cuas.notification.infrastructure.persistence.entity.DeliveryReceiptEntity;

/** Spring Data repository for {@code delivery_receipts}. */
public interface DeliveryReceiptJpaRepository extends JpaRepository<DeliveryReceiptEntity, Long> {
}
