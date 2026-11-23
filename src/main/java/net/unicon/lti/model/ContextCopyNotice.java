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
 * An LtiContextCopyNotice the platform sent: content was copied from one or more
 * origin contexts (courses) into a new or existing context.
 */
@Getter
@Setter
@Entity
@Table(name = "lti_context_copy_notice")
public class ContextCopyNotice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private long contextCopyNoticeId;

    // the platform's id for the notice; a redelivered notice carries the same one
    @Column(nullable = false, unique = true)
    private String noticeId;

    @Column(nullable = false)
    private long keyId;

    // the deployment the notice came from; for a copied course this can be a new installation
    // that isn't registered with the tool, in which case keyId is the platform's registration
    private String deploymentId;

    private String noticeTimestamp;

    // the LTI context id of the course the content was copied into
    @Column(nullable = false, length = 4096)
    private String contextKey;

    @Column(length = 4096)
    private String contextTitle;

    // the LTI context ids of the courses the content came from, comma separated
    @Column(length = 4096)
    private String originContextKeys;

}
