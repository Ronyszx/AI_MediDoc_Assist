package com.mediassist.platform.audit.infrastructure.persistence;

import com.mediassist.platform.audit.domain.AuditEvent;
import com.mediassist.platform.audit.domain.AuditEventRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAuditEventRepository extends JpaRepository<AuditEvent, Long>, AuditEventRepository {
}
