package com.jobtracker.jobservice.controller;

import com.jobtracker.jobservice.dto.RegisterRequest;
import com.jobtracker.jobservice.exception.EmailAlreadyUsedException;
import com.jobtracker.jobservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** Login and register pages for the Thymeleaf UI. (POST /login is handled by Spring Security.) */
@Controller
@RequiredArgsConstructor
public class AuthPageController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
                           BindingResult result) {
        if (result.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.register(request);
        } catch (EmailAlreadyUsedException e) {
            result.rejectValue("email", "duplicate", "This email is already registered");
            return "auth/register";
        }
        return "redirect:/login?registered";
    }

    @GetMapping("/logout-confirm")
    public String logoutConfirmPage() {
        return "auth/logout";
    }
}