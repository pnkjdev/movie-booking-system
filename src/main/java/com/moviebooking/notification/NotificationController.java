package com.moviebooking.notification;

import com.moviebooking.auth.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Delivered and pending notifications for the current user")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public record NotificationResponse(Long id, NotificationType type, String subject, String body,
                                       NotificationStatus status, Instant createdAt, Instant sentAt) {
    }

    @Operation(summary = "List my notifications (newest first)")
    @GetMapping
    @Transactional(readOnly = true)
    public List<NotificationResponse> myNotifications(@AuthenticationPrincipal UserPrincipal principal) {
        return notificationRepository.findByUserIdOrderByIdDesc(principal.id()).stream()
                .map(notification -> new NotificationResponse(notification.getId(), notification.getType(),
                        notification.getSubject(), notification.getBody(), notification.getStatus(),
                        notification.getCreatedAt(), notification.getSentAt()))
                .toList();
    }
}
