package net.unicon.lti.repository;

import net.unicon.lti.model.ContextCopyNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface ContextCopyNoticeRepository extends JpaRepository<ContextCopyNotice, Long> {

    boolean existsByNoticeId(String noticeId);

    long countByKeyId(long keyId);

}
