package dev.jamie.nexusdesk.web;

import dev.jamie.nexusdesk.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@ControllerAdvice
public class PageAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(PageAdvice.class);
    private final UserService users;
    public PageAdvice(UserService users) { this.users = users; }
    @ModelAttribute void common(Authentication auth, Model model) {
        if (auth != null && !(auth instanceof AnonymousAuthenticationToken)) model.addAttribute("me", users.current(auth));
        model.addAttribute("categories", Rules.CATEGORIES);
        model.addAttribute("priorities", Rules.PRIORITIES);
        model.addAttribute("statuses", Rules.STATUSES);
        model.addAttribute("assetTypes", Rules.ASSET_TYPES);
    }
    @ExceptionHandler(Problem.class)
    String problem(Problem error, HttpServletResponse response, Model model) {
        response.setStatus(error.status()); model.addAttribute("message", error.getMessage()); return "error";
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    String duplicate(HttpServletResponse response, Model model) {
        response.setStatus(409);
        model.addAttribute("message", "That email or serial number may already be in use, or the item changed. Check the details and try again.");
        return "error";
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    String invalid(HttpServletResponse response, Model model) {
        response.setStatus(400); model.addAttribute("message", "Some details are missing or invalid. Check the form and try again."); return "error";
    }
    @ExceptionHandler(DataAccessException.class)
    String database(DataAccessException error, HttpServletResponse response, Model model) {
        LOG.error("Database request failed ({})", error.getClass().getSimpleName());
        response.setStatus(503); model.addAttribute("message", "The database couldn't complete that request. Please try again shortly."); return "error";
    }
}
