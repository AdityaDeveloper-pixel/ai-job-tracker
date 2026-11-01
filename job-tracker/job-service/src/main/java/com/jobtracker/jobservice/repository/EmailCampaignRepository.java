package com.jobtracker.jobservice.repository;

import com.jobtracker.jobservice.entity.EmailCampaign;
import com.jobtracker.jobservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long> {

    List<EmailCampaign> findByUserOrderByCreatedAtDesc(User user);

    Optional<EmailCampaign> findByIdAndUser(Long id, User user);
}