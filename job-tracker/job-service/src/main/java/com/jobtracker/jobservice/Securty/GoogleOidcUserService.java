package com.jobtracker.jobservice.Securty;

import com.jobtracker.jobservice.entity.User;
import com.jobtracker.jobservice.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GoogleOidcUserService extends OidcUserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public GoogleOidcUserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser google = super.loadUser(request);

        String email = google.getEmail();
        if (email == null || !Boolean.TRUE.equals(google.getEmailVerified())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_verified"), "Google account email is not verified");
        }
        email = email.trim().toLowerCase();

        User user = users.findByEmail(email).orElse(null);

        if (user == null) {
            String name = google.getFullName() != null ? google.getFullName() : email;
            users.save(User.builder()
                    .name(name)
                    .email(email)
                    .passwordHash(encoder.encode(UUID.randomUUID().toString()))
                    .provider("GOOGLE")
                    .build());
        } else if (!"GOOGLE".equals(user.getProvider())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_exists"), "Email already registered with a password");
        }

        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                google.getIdToken(), google.getUserInfo(), "email");
    }
}