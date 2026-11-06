package com.misael.financialservice;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.misael.financialservice.entities.User;
import com.misael.financialservice.services.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuthorizationService authorizationService;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("delete from transaction_tb");
        jdbcTemplate.update("delete from user_tb");
    }

    @Test
    void registrationAndLoginReturnSafeResponses() throws Exception {
        MvcResult registration = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("maria@example.com", "52998224725")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.wallet").value(0.00))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        JsonNode user = objectMapper.readTree(registration.getResponse().getContentAsString());
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"maria@example.com","password":"SecurePass123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.get("id").asLong()))
                .andExpect(jsonPath("$.email").value("maria@example.com"));
    }

    @Test
    void exposesOpenApiAndSwaggerWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/register'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingInvalidAndInsufficientlyPrivilegedCredentials() throws Exception {
        AccountSession owner = registerAndLogin("owner@example.com", "52998224725");
        AccountSession other = registerAndLogin("other@example.com", "11144477735");

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/users/me").header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/" + other.id()).header("Authorization", bearer(owner.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        mockMvc.perform(get("/users").header("Authorization", bearer(owner.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/" + other.id() + "/credits")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100.00}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void transfersAreAtomicAndHistoryIsScopedToParticipants() throws Exception {
        AccountSession sender = registerAndLogin("sender@example.com", "52998224725");
        AccountSession recipient = registerAndLogin("recipient@example.com", "11144477735");
        jdbcTemplate.update("update user_tb set wallet = ? where id = ?",
                new BigDecimal("1000.00"), sender.id());

        MvcResult transfer = mockMvc.perform(post("/transactions")
                        .header("Authorization", bearer(sender.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId":%d,"amount":125.50}
                                """.formatted(recipient.id())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.amount").value(125.50))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andReturn();

        long transactionId = objectMapper.readTree(transfer.getResponse().getContentAsString())
                .get("id").asLong();
        verify(authorizationService).authorize(any(User.class), eq(new BigDecimal("125.50")));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select wallet from user_tb where id = ?", BigDecimal.class, sender.id()))
                .isEqualByComparingTo("874.50");
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select wallet from user_tb where id = ?", BigDecimal.class, recipient.id()))
                .isEqualByComparingTo("125.50");

        mockMvc.perform(get("/transactions").header("Authorization", bearer(sender.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(transactionId));
        mockMvc.perform(get("/transactions").header("Authorization", bearer(registerAndLogin(
                        "uninvolved@example.com", "11222333000181").token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void insufficientFundsAndInvalidRecipientsDoNotCreateTransactions() throws Exception {
        AccountSession sender = registerAndLogin("empty@example.com", "52998224725");
        AccountSession recipient = registerAndLogin("payee@example.com", "11144477735");

        mockMvc.perform(post("/transactions")
                        .header("Authorization", bearer(sender.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId":%d,"amount":1.00}
                                """.formatted(recipient.id())))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/transactions")
                        .header("Authorization", bearer(sender.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId":999999,"amount":1.00}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/transactions")
                        .header("Authorization", bearer(sender.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId":%d,"amount":0.50}
                                """.formatted(recipient.id())))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transaction_tb", Integer.class)).isZero();
    }

    @Test
    void concurrentTransfersCannotOverspendAnAccount() throws Exception {
        AccountSession sender = registerAndLogin("concurrent-sender@example.com", "52998224725");
        AccountSession firstRecipient = registerAndLogin("concurrent-one@example.com", "11144477735");
        AccountSession secondRecipient = registerAndLogin("concurrent-two@example.com", "11222333000181");
        jdbcTemplate.update("update user_tb set wallet = ? where id = ?",
                new BigDecimal("100.00"), sender.id());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> transferStatus(sender, firstRecipient, "80.00"));
            Future<Integer> second = executor.submit(() -> transferStatus(sender, secondRecipient, "80.00"));
            int firstStatus = first.get();
            int secondStatus = second.get();

            org.assertj.core.api.Assertions.assertThat(java.util.List.of(firstStatus, secondStatus))
                    .containsExactlyInAnyOrder(201, 422);
        } finally {
            executor.shutdownNow();
        }

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select wallet from user_tb where id = ?", BigDecimal.class, sender.id()))
                .isEqualByComparingTo("20.00");
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transaction_tb", Integer.class)).isEqualTo(1);
    }

    @Test
    void administratorCreditsAreAuthenticatedAndRecordedInHistory() throws Exception {
        jdbcTemplate.update("""
                insert into user_tb (complete_name, email, document, password_hash, wallet, user_type, role, version)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, "JavaBank Administrator", "admin@example.com", null,
                passwordEncoder.encode("AdminSecure123!"), BigDecimal.ZERO, "COMMON", "ADMIN", 0L);
        long adminId = jdbcTemplate.queryForObject(
                "select id from user_tb where email = ?", Long.class, "admin@example.com");
        AccountSession recipient = registerAndLogin("credited@example.com", "52998224725");
        String adminToken = login("admin@example.com", "AdminSecure123!");

        MvcResult creditResult = mockMvc.perform(post("/admin/users/" + recipient.id() + "/credits")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":500.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(adminId))
                .andExpect(jsonPath("$.recipientId").value(recipient.id()))
                .andExpect(jsonPath("$.type").value("ADMIN_CREDIT"))
                .andReturn();

        long creditId = objectMapper.readTree(creditResult.getResponse().getContentAsString())
                .get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select wallet from user_tb where id = ?", BigDecimal.class, recipient.id()))
                .isEqualByComparingTo("500.00");
        mockMvc.perform(get("/transactions/" + creditId)
                        .header("Authorization", bearer(recipient.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ADMIN_CREDIT"));
    }

    @Test
    void merchantAccountsCannotInitiateTransfers() throws Exception {
        AccountSession merchant = registerAndLogin("merchant@example.com", "11222333000181");
        AccountSession recipient = registerAndLogin("merchant-payee@example.com", "52998224725");
        jdbcTemplate.update("update user_tb set wallet = ? where id = ?",
                new BigDecimal("100.00"), merchant.id());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", bearer(merchant.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId":%d,"amount":10.00}
                                """.formatted(recipient.id())))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transaction_tb", Integer.class)).isZero();
    }

    private AccountSession registerAndLogin(String email, String document) throws Exception {
        MvcResult registration = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, document)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(registration.getResponse().getContentAsString())
                .get("id").asLong();
        return new AccountSession(id, login(email, "SecurePass123!"));
    }

    private String login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
        return token;
    }

    private String registerBody(String email, String document) {
        return """
                {"completeName":"Test User","email":"%s","password":"SecurePass123!","document":"%s"}
                """.formatted(email, document);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private int transferStatus(AccountSession sender, AccountSession recipient, String amount) throws Exception {
        return mockMvc.perform(post("/transactions")
                        .header("Authorization", bearer(sender.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId":%d,"amount":%s}
                                """.formatted(recipient.id(), amount)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private record AccountSession(long id, String token) {
    }
}
