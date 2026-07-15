package com.moviebooking.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByStatusOrderByIdAsc(NotificationStatus status);

    List<Notification> findByUserIdOrderByIdDesc(Long userId);
}
