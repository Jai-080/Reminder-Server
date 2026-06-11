package com.reminder.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reminder.server.note.*;
import com.reminder.server.reminder.*;
import com.reminder.server.payment.*;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CrudSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuickNoteRepository quickNoteRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private MonthlyPaymentRepository monthlyPaymentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String tokenA;
    private String tokenB;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() throws Exception {
        // Register and login User A
        String uuidA = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest regA = RegisterRequest.builder()
                .username("user_a_" + uuidA)
                .email("usera_" + uuidA + "@example.com")
                .password("Password123!")
                .deviceName("DeviceA")
                .platform("Android")
                .build();

        MvcResult resultA = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regA)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse respA = objectMapper.readValue(resultA.getResponse().getContentAsString(), AuthResponse.class);
        tokenA = respA.getAccessToken();
        userA = userRepository.findById(respA.getUserId()).orElseThrow();

        // Register and login User B
        String uuidB = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest regB = RegisterRequest.builder()
                .username("user_b_" + uuidB)
                .email("userb_" + uuidB + "@example.com")
                .password("Password123!")
                .deviceName("DeviceB")
                .platform("Windows")
                .build();

        MvcResult resultB = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regB)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse respB = objectMapper.readValue(resultB.getResponse().getContentAsString(), AuthResponse.class);
        tokenB = respB.getAccessToken();
        userB = userRepository.findById(respB.getUserId()).orElseThrow();
    }

    @Test
    void testCrudOwnershipSecurityAndSoftDelete() throws Exception {
        // ==========================================
        // 1. User A creates resources
        // ==========================================

        // Note
        NoteRequest noteReq = NoteRequest.builder()
                .text("User A Note Text")
                .isCompleted(false)
                .position(1)
                .build();

        MvcResult noteCreateResult = mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.text").value("User A Note Text"))
                .andExpect(jsonPath("$.isCompleted").value(false))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        NoteResponse noteResp = objectMapper.readValue(noteCreateResult.getResponse().getContentAsString(), NoteResponse.class);
        Long noteId = noteResp.getId();

        // Reminder
        Long reminderTime = Instant.now().plusSeconds(3600).toEpochMilli();
        ReminderRequest reminderReq = ReminderRequest.builder()
                .text("User A Reminder")
                .reminderTime(reminderTime)
                .isExpired(false)
                .snoozedTime(null)
                .build();

        MvcResult reminderCreateResult = mockMvc.perform(post("/api/reminders")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reminderReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.text").value("User A Reminder"))
                .andExpect(jsonPath("$.isExpired").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        ReminderResponse reminderResp = objectMapper.readValue(reminderCreateResult.getResponse().getContentAsString(), ReminderResponse.class);
        Long reminderId = reminderResp.getId();

        // Payment
        Long dueDate = Instant.now().plusSeconds(86400 * 5).toEpochMilli();
        PaymentRequest paymentReq = PaymentRequest.builder()
                .name("User A Payment")
                .dueDate(dueDate)
                .completed(false)
                .build();

        MvcResult paymentCreateResult = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("User A Payment"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        PaymentResponse paymentResp = objectMapper.readValue(paymentCreateResult.getResponse().getContentAsString(), PaymentResponse.class);
        Long paymentId = paymentResp.getId();

        // ==========================================
        // 2. User B attempts to access/update/delete User A's resources
        // Expected: 404 Not Found for direct resource access
        // ==========================================

        // Note ownership checks
        mockMvc.perform(get("/api/notes/" + noteId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/notes/" + noteId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/notes/" + noteId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Reminder ownership checks
        mockMvc.perform(get("/api/reminders/" + reminderId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/reminders/" + reminderId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reminderReq)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/reminders/" + reminderId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Payment ownership checks
        mockMvc.perform(get("/api/payments/" + paymentId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/payments/" + paymentId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentReq)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/payments/" + paymentId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // ==========================================
        // 3. User B gets list of resources
        // Expected: Empty list (User A's resources should not show up)
        // ==========================================
        mockMvc.perform(get("/api/notes")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/reminders")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/payments")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // ==========================================
        // 4. Update timestamps check (User A modifies their own resources)
        // ==========================================
        Thread.sleep(100); // Sleep briefly to guarantee different updatedAt timestamp

        NoteRequest noteUpdate = NoteRequest.builder()
                .text("User A Note Text Updated")
                .isCompleted(true)
                .position(2)
                .build();

        MvcResult noteUpdateResult = mockMvc.perform(put("/api/notes/" + noteId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("User A Note Text Updated"))
                .andExpect(jsonPath("$.isCompleted").value(true))
                .andExpect(jsonPath("$.position").value(2))
                .andReturn();

        NoteResponse noteUpdatedResp = objectMapper.readValue(noteUpdateResult.getResponse().getContentAsString(), NoteResponse.class);
        // Verify createdAt remains the same, updatedAt changes
        assertThat(noteUpdatedResp.getCreatedAt().toEpochMilli()).isEqualTo(noteResp.getCreatedAt().toEpochMilli());
        assertThat(noteUpdatedResp.getUpdatedAt()).isAfter(noteResp.getUpdatedAt());

        // ==========================================
        // 5. Validation constraints check (Reject invalid payloads with 400 Bad Request)
        // ==========================================
        NoteRequest invalidNote = NoteRequest.builder()
                .text("") // Blank (should fail @NotBlank)
                .isCompleted(null) // null (should fail @NotNull)
                .position(null) // null (should fail @NotNull)
                .build();

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidNote)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        // ==========================================
        // 6. Soft Delete verify
        // ==========================================
        mockMvc.perform(delete("/api/notes/" + noteId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/reminders/" + reminderId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/payments/" + paymentId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // User A list should be empty now
        mockMvc.perform(get("/api/notes")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/reminders")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/payments")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // Verify that database records are NOT physically deleted but are flagged
        Optional<QuickNote> dbNote = quickNoteRepository.findById(noteId);
        assertThat(dbNote).isPresent();
        assertThat(dbNote.get().getDeleted()).isTrue();
        assertThat(dbNote.get().getDeletedAt()).isNotNull();

        Optional<Reminder> dbReminder = reminderRepository.findById(reminderId);
        assertThat(dbReminder).isPresent();
        assertThat(dbReminder.get().getDeleted()).isTrue();
        assertThat(dbReminder.get().getDeletedAt()).isNotNull();

        Optional<MonthlyPayment> dbPayment = monthlyPaymentRepository.findById(paymentId);
        assertThat(dbPayment).isPresent();
        assertThat(dbPayment.get().getDeleted()).isTrue();
        assertThat(dbPayment.get().getDeletedAt()).isNotNull();
    }
}
