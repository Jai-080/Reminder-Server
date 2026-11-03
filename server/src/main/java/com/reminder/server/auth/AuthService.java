package com.reminder.server.auth;

import com.reminder.server.device.Device;
import com.reminder.server.device.DeviceRepository;
import com.reminder.server.security.CustomUserPrincipal;
import com.reminder.server.security.JwtService;
import com.reminder.server.user.User;
import com.reminder.server.user.UserRepository;
import com.reminder.server.util.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("[AUTH] Register attempt for email='{}' username='{}' platform='{}' deviceName='{}'",
                maskEmail(request.getEmail()), request.getUsername(), request.getPlatform(), request.getDeviceName());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("[AUTH] Register failed: EMAIL_ALREADY_EXISTS for email='{}'", maskEmail(request.getEmail()));
            throw new IllegalArgumentException("Email is already taken");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            log.warn("[AUTH] Register failed: USERNAME_ALREADY_EXISTS for username='{}'", request.getUsername());
            throw new IllegalArgumentException("Username is already taken");
        }

        // Create User
        User userToSave = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .createdAt(System.currentTimeMillis())
                .build();
        final User user = userRepository.save(userToSave);

        // Resolve Device
        Device device = deviceRepository.findByUserAndDeviceNameAndPlatform(user, request.getDeviceName(), request.getPlatform())
                .orElseGet(() -> Device.builder()
                        .user(user)
                        .deviceName(request.getDeviceName())
                        .platform(request.getPlatform())
                        .build());
        device.setLastSeen(System.currentTimeMillis());
        device = deviceRepository.save(device);

        // Generate Tokens
        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String rawRefreshToken = UUID.randomUUID().toString();

        // Save Hashed Refresh Token
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .device(device)
                .tokenHash(hashToken(rawRefreshToken))
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusMillis(604800000)) // 7 days
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        log.info("[AUTH] Register successful for userId={} username='{}' deviceId={}",
                user.getId(), maskEmail(user.getUsername()), device.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("[AUTH] Login attempt for identifier='{}' platform='{}' deviceName='{}'",
                maskEmail(request.getEmail()), request.getPlatform(), request.getDeviceName());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("[AUTH] Login failed: USER_NOT_FOUND for identifier='{}'", maskEmail(request.getEmail()));
                    return new BadCredentialsException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("[AUTH] Login failed: BAD_CREDENTIALS for userId={} email='{}'", user.getId(), maskEmail(request.getEmail()));
            throw new BadCredentialsException("Invalid credentials");
        }

        // Resolve Device
        Device device = deviceRepository.findByUserAndDeviceNameAndPlatform(user, request.getDeviceName(), request.getPlatform())
                .orElseGet(() -> Device.builder()
                        .user(user)
                        .deviceName(request.getDeviceName())
                        .platform(request.getPlatform())
                        .build());
        device.setLastSeen(System.currentTimeMillis());
        device = deviceRepository.save(device);

        // Revoke old refresh tokens for this device
        refreshTokenRepository.deleteByDevice(device);

        // Generate Tokens
        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String rawRefreshToken = UUID.randomUUID().toString();

        // Save Hashed Refresh Token
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .device(device)
                .tokenHash(hashToken(rawRefreshToken))
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusMillis(604800000)) // 7 days
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        log.info("[AUTH] Login successful for userId={} username='{}' deviceId={} deviceName='{}' platform='{}'",
                user.getId(), maskEmail(user.getUsername()), device.getId(), device.getDeviceName(), device.getPlatform());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());
        String hashPrefix = tokenHash.length() >= 8 ? tokenHash.substring(0, 8) : tokenHash;
        log.info("[AUTH] Refresh token attempt (hashPrefix={})", hashPrefix);

        RefreshToken refreshTokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("[AUTH] Refresh failed: TOKEN_NOT_FOUND (hashPrefix={})", hashPrefix);
                    return new IllegalArgumentException("Invalid or expired refresh token");
                });

        if (refreshTokenEntity.getExpiresAt().isBefore(Instant.now())) {
            log.warn("[AUTH] Refresh failed: TOKEN_EXPIRED (hashPrefix={}, expiredAt={})",
                    hashPrefix, refreshTokenEntity.getExpiresAt());
            refreshTokenRepository.delete(refreshTokenEntity);
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        User user = refreshTokenEntity.getUser();
        Device device = refreshTokenEntity.getDevice();

        // Rotate Refresh Token
        refreshTokenRepository.delete(refreshTokenEntity);

        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        String newAccessToken = jwtService.generateAccessToken(principal);
        String newRawRefreshToken = UUID.randomUUID().toString();

        RefreshToken newRefreshTokenEntity = RefreshToken.builder()
                .user(user)
                .device(device)
                .tokenHash(hashToken(newRawRefreshToken))
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusMillis(604800000))
                .build();
        refreshTokenRepository.save(newRefreshTokenEntity);

        log.info("[AUTH] Refresh token rotated: revoked old token (hashPrefix={}), issued new token for userId={} deviceId={}",
                hashPrefix, user.getId(), device.getId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());
        String hashPrefix = tokenHash.length() >= 8 ? tokenHash.substring(0, 8) : tokenHash;
        log.info("[AUTH] Logout attempt (hashPrefix={})", hashPrefix);
        refreshTokenRepository.deleteByTokenHash(tokenHash);
        log.info("[AUTH] Logout completed: revoked refresh token (hashPrefix={})", hashPrefix);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String maskEmail(String email) {
        return LoggingUtils.maskEmail(email);
    }
}
