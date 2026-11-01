package com.jobtracker.jobservice.service;

import com.jobtracker.jobservice.dto.RegisterRequest;
import com.jobtracker.jobservice.entity.User;
import com.jobtracker.jobservice.exception.EmailAlreadyUsedException;
import com.jobtracker.jobservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public User register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .passwordHash(encoder.encode(request.getPassword()))
                .build();

        return users.save(user);
    }
}