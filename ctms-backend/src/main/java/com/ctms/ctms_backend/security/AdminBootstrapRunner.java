package com.ctms.ctms_backend.security;

import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.RoleRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the very first ADMIN user from env vars if the users table is empty. There is no
 * user-management UI yet (that lands with Epic 9 / Phase 10), so this is the only bootstrap path
 * until then. No-ops (with a warning) if the env vars aren't set, rather than inventing a default
 * password.
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final String bootstrapUsername;
    private final String bootstrapEmail;
    private final String bootstrapPassword;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicyValidator,
            @Value("${ADMIN_BOOTSTRAP_USERNAME:}") String bootstrapUsername,
            @Value("${ADMIN_BOOTSTRAP_EMAIL:}") String bootstrapEmail,
            @Value("${ADMIN_BOOTSTRAP_PASSWORD:}") String bootstrapPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (bootstrapUsername.isBlank() || bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            log.warn(
                    "No users exist yet and ADMIN_BOOTSTRAP_USERNAME/EMAIL/PASSWORD are not all set -- "
                            + "skipping admin bootstrap. Set them and restart to create the first admin user.");
            return;
        }

        User admin = new User();
        admin.setUsername(bootstrapUsername);
        admin.setEmail(bootstrapEmail);
        admin.setFullName("System Administrator");

        passwordPolicyValidator.validate(admin, bootstrapPassword);
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));

        Role adminRole = roleRepository
                .findByCode(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing -- check V1 migration seed data"));
        admin.setRoles(new HashSet<>(java.util.List.of(adminRole)));

        userRepository.save(admin);
        log.info("Bootstrapped initial admin user '{}'", bootstrapUsername);
    }
}
