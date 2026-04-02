package com.example.users.service;

import com.example.users.dto.CreateUserRequest;
import com.example.users.exception.UserNotFoundException;
import com.example.users.model.AuditLog;
import com.example.users.model.User;
import com.example.users.repository.AuditRepository;
import com.example.users.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interview Problem #2 — Spring Boot / Service Layer
 * Difficulty: Hard | Issues to find: 3
 *
 * A service that creates users and caches lookups by ID.
 * All unit tests pass and it ships to staging without errors.
 * Find 3 things that are wrong or should be refactored.
 */
@Service
public class UserService {

    private final UserRepository userRepo;
    private final AuditRepository auditRepo;
    private final PasswordEncoder encoder;

    public UserService(UserRepository userRepo,
                       AuditRepository auditRepo,
                       PasswordEncoder encoder) {
        this.userRepo  = userRepo;
        this.auditRepo = auditRepo;
        this.encoder   = encoder;
    }

    public void createUser(CreateUserRequest request) {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        userRepo.save(user);

        AuditLog log = new AuditLog("USER_CREATED", user.getId());
        auditRepo.save(log);
    }

    @Cacheable("users")
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepo.findById(id)
                       .orElseThrow(UserNotFoundException::new);
    }
}
