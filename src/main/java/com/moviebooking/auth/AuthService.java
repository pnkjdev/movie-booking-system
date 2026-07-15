package com.moviebooking.auth;

import com.moviebooking.auth.dto.AuthDtos.AuthResponse;
import com.moviebooking.auth.dto.AuthDtos.LoginRequest;
import com.moviebooking.auth.dto.AuthDtos.RegisterRequest;
import com.moviebooking.auth.dto.AuthDtos.UserResponse;
import com.moviebooking.auth.security.JwtService;
import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "An account with this email already exists");
        }
        User user = userRepository.save(User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.CUSTOMER)
                .createdAt(clock.instant())
                .build());
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwtService.issueToken(user),
                jwtService.expiry().toSeconds(),
                UserResponse.from(user));
    }
}
