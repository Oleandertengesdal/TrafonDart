package no.trafondart.auth;

import no.trafondart.config.AppProperties;
import no.trafondart.user.User;
import no.trafondart.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * Creates the very first admin from {@code BOOTSTRAP_ADMIN_USERNAME} / {@code BOOTSTRAP_ADMIN_PASSWORD}
 * when – and only when – the users table is empty. After that the variables are ignored and can be removed.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public BootstrapAdminRunner(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        AppProperties.BootstrapAdmin admin = props.bootstrapAdmin();
        if (admin == null || isBlank(admin.username()) || isBlank(admin.password())) {
            log.warn("No users exist yet. Set BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD to create the first admin.");
            return;
        }
        if (!admin.username().matches(no.trafondart.user.UserDtos.USERNAME_PATTERN)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME must be 3–24 letters, digits, '_', '.' or '-'");
        }
        int bytes = admin.password().getBytes(StandardCharsets.UTF_8).length;
        if (admin.password().length() < AuthService.MIN_PASSWORD_LENGTH || bytes > AuthService.MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be 10–72 characters");
        }
        users.save(new User(admin.username(), admin.realName(), encoder.encode(admin.password()), User.Role.ADMIN));
        log.info("Created bootstrap admin '{}'. Change the password after first login and remove the env vars.",
                admin.username());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
