package com.jobtracker.jobservice.repository;

import com.jobtracker.jobservice.entity.CampaignRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long> {
    Optional<CampaignRecipient> findById(Long id);
}
