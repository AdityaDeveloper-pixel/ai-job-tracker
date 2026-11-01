package com.jobtracker.jobservice.service;

import com.jobtracker.jobservice.dto.EmailTemplateDTO;
import com.jobtracker.jobservice.entity.EmailTemplate;
import com.jobtracker.jobservice.exception.ResourceNotFoundException;
import com.jobtracker.jobservice.repository.EmailTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateService {

    private final EmailTemplateRepository repository;
    private final CurrentUserService currentUser;

    public List<EmailTemplate> getAllTemplates() {
        return repository.findByUserOrderByUpdatedAtDesc(currentUser.get());
    }

    public EmailTemplate getById(Long id) {
        return repository.findByIdAndUser(id, currentUser.get())
                .orElseThrow(() -> new ResourceNotFoundException("Email template", id));
    }

    public EmailTemplate create(EmailTemplateDTO dto) {
        EmailTemplate template = EmailTemplate.builder()
                .user(currentUser.get())
                .name(dto.getName())
                .subject(dto.getSubject())
                .body(dto.getBody())
                .build();

        EmailTemplate saved = repository.save(template);
        log.info("Created email template: id={}, name='{}'", saved.getId(), saved.getName());
        return saved;
    }

    public EmailTemplate update(Long id, EmailTemplateDTO dto) {
        EmailTemplate template = getById(id);

        template.setName(dto.getName());
        template.setSubject(dto.getSubject());
        template.setBody(dto.getBody());

        EmailTemplate saved = repository.save(template);
        log.info("Updated email template: id={}, name='{}'", saved.getId(), saved.getName());
        return saved;
    }

    public void delete(Long id) {
        repository.delete(getById(id));
        log.info("Deleted email template: id={}", id);
    }
}