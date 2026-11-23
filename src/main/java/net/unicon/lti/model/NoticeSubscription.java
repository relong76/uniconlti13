package net.unicon.lti.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Records that this tool registered a notice handler with a platform deployment's
 * Platform Notification Service, so a launch doesn't register it again.
 */
@Getter
@Setter
@Entity
@Table(name = "lti_notice_subscription")
public class NoticeSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private long subscriptionId;

    @Column(nullable = false)
    private long keyId;

    @Column(nullable = false)
    private String noticeType;

    @Column(nullable = false, length = 4096)
    private String pnsUrl;

    @Column(nullable = false, length = 4096)
    private String handlerUrl;

}
