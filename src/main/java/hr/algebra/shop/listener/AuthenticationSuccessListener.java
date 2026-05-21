package hr.algebra.shop.listener;

import hr.algebra.shop.model.LoginHistory;
import hr.algebra.shop.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AuthenticationSuccessListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final LoginHistoryRepository loginHistoryRepository;

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        String ip = "unknown";
        if (event.getAuthentication().getDetails() instanceof WebAuthenticationDetails details) {
            ip = details.getRemoteAddress();
        }
        loginHistoryRepository.save(LoginHistory.builder()
                .username(username)
                .ipAddress(ip)
                .loginAt(LocalDateTime.now())
                .build());
    }
}