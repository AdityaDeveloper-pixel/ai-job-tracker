package com.jobtracker.jobservice.service;

import com.jobtracker.jobservice.entity.User;
import com.jobtracker.jobservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository users;

    /** The user making the current request. */
    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Not logged in");
        }
        return users.findByEmail(auth.getName().trim().toLowerCase())
                .orElseThrow(() -> new AccessDeniedException("Unknown user"));
    }
}