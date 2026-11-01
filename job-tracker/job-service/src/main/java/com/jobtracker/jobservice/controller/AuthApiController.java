package com.jobtracker.jobservice.controller;

import com.jobtracker.jobservice.Securty.JwtService;
import com.jobtracker.jobservice.dto.AuthResponse;
import com.jobtracker.jobservice.dto.LoginRequest;
import com.jobtracker.jobservice.dto.RegisterRequest;
import com.jobtracker.jobservice.entity.User;
import com.jobtracker.jobservice.exception.EmailAlreadyUsedException;
import com.jobtracker.jobservice.repository.UserRepository;
import com.jobtracker.jobservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

/** JSON login/register for Angular, Android and other API clients. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final UserService userService;
    private final UserRepository users;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        AuthResponse body = new AuthResponse(jwtService.generate(user.getEmail()), user.getName(), user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // throws BadCredentialsException if email/password are wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        User user = users.findByEmail(email).orElseThrow();
        return new AuthResponse(jwtService.generate(email), user.getName(), user.getEmail());
    }

    // Handlers inside the controller win over the global @ControllerAdvice,
    // so API clients always get small JSON errors instead of an HTML error page.

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(EmailAlreadyUsedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already registered"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleBadLogin(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid email or password"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}