package no.trafondart.account;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import no.trafondart.auth.AuthUser;
import no.trafondart.auth.SessionCookies;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Download your own data, or delete your account. Only ever for the logged-in member themselves. */
@RestController
public class AccountController {

    private final AccountService accounts;
    private final SessionCookies cookies;

    public AccountController(AccountService accounts, SessionCookies cookies) {
        this.accounts = accounts;
        this.cookies = cookies;
    }

    @GetMapping("/api/users/me/export")
    public ResponseEntity<AccountService.Export> export() {
        AccountService.Export export = accounts.export(AuthUser.current().id());
        String name = "trafondart-" + export.profile().username() + "-" + LocalDate.now(ZoneOffset.UTC) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(export);
    }

    @DeleteMapping("/api/users/me")
    public ResponseEntity<Void> delete(@Valid @RequestBody AccountService.DeleteAccountRequest req, HttpServletResponse response) {
        accounts.delete(AuthUser.current().id(), req);
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }
}
