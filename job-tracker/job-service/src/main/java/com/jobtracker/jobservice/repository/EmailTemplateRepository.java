package com.jobtracker.jobservice.repository;

import com.jobtracker.jobservice.entity.EmailTemplate;
import com.jobtracker.jobservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    List<EmailTemplate> findByUserOrderByUpdatedAtDesc(User user);

    Optional<EmailTemplate> findByIdAndUser(Long id, User user);
}