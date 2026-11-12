package net.unicon.lti.repository;

import net.unicon.lti.model.NoticeSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
public interface NoticeSubscriptionRepository extends JpaRepository<NoticeSubscription, Long> {

    Optional<NoticeSubscription> findByKeyIdAndNoticeType(long keyId, String noticeType);

    long countByKeyId(long keyId);

}
