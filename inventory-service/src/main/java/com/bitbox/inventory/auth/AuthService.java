package com.bitbox.inventory.auth;

import java.util.Locale;

import com.bitbox.inventory.auth.AuthExceptions.EmailAlreadyRegisteredException;
import com.bitbox.inventory.auth.AuthExceptions.InvalidCredentialsException;
import com.bitbox.inventory.security.JwtService;
import com.bitbox.inventory.user.User;
import com.bitbox.inventory.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * The existence check gives a friendly error; the unique constraint on users.email is what
     * actually guarantees uniqueness under concurrent registrations (mapped to 409 as well).
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password())));
        return new UserResponse(user.getId(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(InvalidCredentialsException::new);
        return jwtService.issueToken(user);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
