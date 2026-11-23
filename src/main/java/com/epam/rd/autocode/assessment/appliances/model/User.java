package com.epam.rd.autocode.assessment.appliances.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "{validation.user.name.required}")
    @Size(max = 100, message = "{validation.user.name.size}")
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank(message = "{validation.user.email.required}")
    @Email(message = "{validation.user.email.invalid}")
    @Column(nullable = false, unique = true)
    private String email;

    /** Stores the (BCrypt) hash. */
    @NotBlank(message = "{validation.user.password.required}")
    @Column(nullable = false)
    private String password;

    /** Spring Security role derived from the subclass (not persisted). */
    @Transient
    public abstract String getRole();
}