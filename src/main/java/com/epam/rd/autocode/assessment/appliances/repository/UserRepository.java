package com.epam.rd.autocode.assessment.appliances.repository;

import com.epam.rd.autocode.assessment.appliances.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Used for database-backed authentication (works for both Client and Employee). */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** For edit forms: is the email taken by someone other than this user? */
    boolean existsByEmailAndIdNot(String email, Long id);
}