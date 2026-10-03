package com.helpdesk.auth.repo;

import com.helpdesk.auth.domain.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteCodeRepository extends JpaRepository<InviteCode, String> {
}
