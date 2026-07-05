package com.ctms.ctms_backend.notification;

import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Page<NotificationResponse> list(
            Principal principal,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.list(currentUser(principal), type, unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    public long unreadCount(Principal principal) {
        return notificationService.unreadCount(currentUser(principal));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(Principal principal, @PathVariable Long id) {
        notificationService.markRead(currentUser(principal), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Principal principal) {
        notificationService.markAllRead(currentUser(principal));
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow(InvalidCredentialsException::new);
    }
}
