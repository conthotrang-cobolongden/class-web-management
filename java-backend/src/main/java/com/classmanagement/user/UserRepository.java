package com.classmanagement.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Page<User> findByRole(String role, Pageable pageable);

    Page<User> findByIsActiveTrue(Pageable pageable);

    Page<User> findByRoleAndIsActiveTrue(String role, Pageable pageable);

    List<User> findByPasswordHashContaining(String fragment);
}
