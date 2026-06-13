package hr.algebra.shop.controller.rest;

import hr.algebra.shop.configuration.JwtUtil;
import hr.algebra.shop.dto.JwtResponseDTO;
import hr.algebra.shop.dto.LoginRequest;
import hr.algebra.shop.dto.RefreshTokenRequestDTO;
import hr.algebra.shop.model.User;
import hr.algebra.shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> login(@RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByUsername(request.username()).orElseThrow();
        String accessToken = jwtUtil.generateToken(user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        return ResponseEntity.ok(JwtResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build());
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<JwtResponseDTO> refreshToken(@RequestBody RefreshTokenRequestDTO request) {
        return userRepository.findByRefreshToken(request.getToken())
                .filter(u -> jwtUtil.isTokenValid(request.getToken()))
                .map(u -> ResponseEntity.ok(JwtResponseDTO.builder()
                        .accessToken(jwtUtil.generateToken(u.getUsername()))
                        .refreshToken(request.getToken())
                        .build()))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequestDTO request) {
        userRepository.findByRefreshToken(request.getToken()).ifPresent(u -> {
            u.setRefreshToken(null);
            userRepository.save(u);
        });
        return ResponseEntity.ok().build();
    }
}
