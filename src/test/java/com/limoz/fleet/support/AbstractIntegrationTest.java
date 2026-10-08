package com.limoz.fleet.support;

import com.limoz.fleet.auth.service.AuthService;
import com.limoz.fleet.auth.dto.AuthResponse;
import com.limoz.fleet.auth.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    public static final String ADMIN_EMAIL = "admin@limoz.rw";
    public static final String ADMIN_PASSWORD = "Admin@12345";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected AuthService authService;

    protected String adminToken;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", EmbeddedPostgresExtension::jdbcUrl);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @BeforeEach
    void authenticateAdmin() {
        adminToken = "Bearer " + login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken();
    }

    protected AuthResponse login(String email, String password) {
        return authService.login(new LoginRequest(email, password));
    }

    /** Bearer token for a user holding exactly one role (created on first use). */
    protected String tokenFor(String roleCode) {
        String email = roleCode.toLowerCase() + "@test.limoz.rw";
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            userService.create(new com.limoz.fleet.user.dto.CreateUserRequest(roleCode, "Tester", email, null, null,
                    "Test@12345", java.util.Set.of(roleCode), null, false));
        }
        return "Bearer " + login(email, "Test@12345").accessToken();
    }

    @Autowired
    protected com.limoz.fleet.user.service.UserService userService;

    @Autowired
    protected com.limoz.fleet.user.repository.UserRepository userRepository;

    protected String toJson(Object value) {
        return json.writeValueAsString(value);
    }
}
