package com.reminder.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reminder.server.device.Device;
import com.reminder.server.device.DeviceRepository;
import com.reminder.server.user.User;
import com.reminder.server.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String testUsername;
    private String testEmail;
    private String testPassword;

    @BeforeEach
    void setUp() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        testUsername = "user_" + uniqueId;
        testEmail = testUsername + "@example.com";
        testPassword = "StrongPassword123!";
    }

    @Test
    void testFullAuthFlowAndSecurityChecks() throws Exception {
        // --- CHECK 6: REGISTER ---
        RegisterRequest regRequest = RegisterRequest.builder()
                .username(testUsername)
                .email(testEmail)
                .password(testPassword)
                .deviceName("Device_Android")
                .platform("Android")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.username").value(testUsername))
                .andReturn();

        AuthResponse regResponse = objectMapper.readValue(regResult.getResponse().getContentAsString(), AuthResponse.class);

        // --- CHECK 3: PASSWORD HASH VERIFICATION ---
        Optional<User> userOpt = userRepository.findByEmail(testEmail);
        assertThat(userOpt).isPresent();
        User user = userOpt.get();
        assertThat(user.getPasswordHash()).startsWith("$2a$"); // BCrypt prefix
        assertThat(user.getPasswordHash()).isNotEqualTo(testPassword); // Never store plain text

        // --- CHECK 4: REFRESH TOKEN HASH SECURITY ---
        String rawRegRefreshToken = regResponse.getRefreshToken();
        String hashedRegToken = hashToken(rawRegRefreshToken);
        Optional<RefreshToken> dbRegTokenOpt = refreshTokenRepository.findByTokenHash(hashedRegToken);
        assertThat(dbRegTokenOpt).isPresent();
        // Database should not store raw UUID token directly
        Iterable<RefreshToken> allTokens = refreshTokenRepository.findAll();
        for (RefreshToken t : allTokens) {
            assertThat(t.getTokenHash()).isNotEqualTo(rawRegRefreshToken);
        }

        // --- CHECK 6 (Duplicate username/email validation checks) ---
        RegisterRequest dupEmailRequest = RegisterRequest.builder()
                .username("another_user")
                .email(testEmail)
                .password(testPassword)
                .deviceName("Device_Android")
                .platform("Android")
                .build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupEmailRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already taken"));

        // Validation constraint check: too short password
        RegisterRequest badRequest = RegisterRequest.builder()
                .username(testUsername)
                .email("invalid-email")
                .password("short")
                .deviceName("Device_Android")
                .platform("Android")
                .build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        // --- CHECK 7: LOGIN & CHECK 5: MULTI-DEVICE LOGIN TEST ---
        // Let's login from Device B (Windows) while Device A (Android) is registered and has session
        LoginRequest loginDeviceB = LoginRequest.builder()
                .email(testEmail)
                .password(testPassword)
                .deviceName("Device_Windows")
                .platform("Windows")
                .build();

        MvcResult loginResultB = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDeviceB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthResponse responseB = objectMapper.readValue(loginResultB.getResponse().getContentAsString(), AuthResponse.class);

        // Verify that BOTH Device A (Android) and Device B (Windows) are present in DB
        Optional<Device> deviceA = deviceRepository.findByUserAndDeviceNameAndPlatform(user, "Device_Android", "Android");
        Optional<Device> deviceB = deviceRepository.findByUserAndDeviceNameAndPlatform(user, "Device_Windows", "Windows");
        assertThat(deviceA).isPresent();
        assertThat(deviceB).isPresent();

        // Verify both sessions have refresh token hashes in the database
        Optional<RefreshToken> tokenA = refreshTokenRepository.findByTokenHash(hashToken(rawRegRefreshToken));
        Optional<RefreshToken> tokenB = refreshTokenRepository.findByTokenHash(hashToken(responseB.getRefreshToken()));
        assertThat(tokenA).isPresent();
        assertThat(tokenB).isPresent(); // Both are active and distinct!

        // --- CHECK 7: LOGIN INVALID CASES ---
        LoginRequest badPassLogin = LoginRequest.builder()
                .email(testEmail)
                .password("WrongPassword")
                .deviceName("Device_Android")
                .platform("Android")
                .build();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPassLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        LoginRequest badEmailLogin = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password(testPassword)
                .deviceName("Device_Android")
                .platform("Android")
                .build();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badEmailLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        // --- CHECK 8: REFRESH ENDPOINT ---
        // Refresh Device B
        RefreshTokenRequest refreshReq = RefreshTokenRequest.builder()
                .refreshToken(responseB.getRefreshToken())
                .build();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthResponse refreshResponse = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), AuthResponse.class);

        // Verify token rotation occurred: Old token hash should be deleted, new token hash created
        assertThat(refreshTokenRepository.findByTokenHash(hashToken(responseB.getRefreshToken()))).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(hashToken(refreshResponse.getRefreshToken()))).isPresent();

        // Refresh with invalid token
        RefreshTokenRequest invalidRefresh = RefreshTokenRequest.builder()
                .refreshToken("invalid-token-uuid-xyz")
                .build();
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRefresh)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));

        // --- CHECK 12: CURRENT USER ENDPOINT `/api/auth/me` ---
        // Try unauthorized
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        // Try authorized using the new refreshed access token
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + refreshResponse.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.username").value(testUsername))
                .andExpect(jsonPath("$.email").value(testEmail));

        // --- CHECK 11: PROTECTED ENDPOINT ---
        // Case 1: No token
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized());

        // Case 2: Invalid token / Tampered token (Check 10)
        mockMvc.perform(get("/api/test/protected")
                        .header("Authorization", "Bearer " + refreshResponse.getAccessToken() + "tamper"))
                .andExpect(status().isUnauthorized());

        // Case 4: Valid token
        mockMvc.perform(get("/api/test/protected")
                        .header("Authorization", "Bearer " + refreshResponse.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This is a protected endpoint. Access granted!"));

        // --- CHECK 9: LOGOUT ENDPOINT ---
        RefreshTokenRequest logoutReq = RefreshTokenRequest.builder()
                .refreshToken(refreshResponse.getRefreshToken())
                .build();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Verify token deleted from database
        assertThat(refreshTokenRepository.findByTokenHash(hashToken(refreshResponse.getRefreshToken()))).isEmpty();

        // Refresh with logged-out token should fail
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
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
            throw new RuntimeException(e);
        }
    }
}
