package com.epam.rd.autocode.assessment.appliances.dto;

import com.epam.rd.autocode.assessment.appliances.validation.PasswordsMatch;
import com.epam.rd.autocode.assessment.appliances.validation.StrongPassword;
import com.epam.rd.autocode.assessment.appliances.validation.UniqueEmail;
import com.epam.rd.autocode.assessment.appliances.validation.UserType;
import com.epam.rd.autocode.assessment.appliances.validation.ValidationGroups.OnCreate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

/**
 * Form object for creating/editing a client (also used for registration).
 * The password is required only on create (group OnCreate); on edit an empty password means "keep the current one".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@UniqueEmail(type = UserType.CLIENT)
@PasswordsMatch
public class ClientDto {

    private Long id;

    @NotBlank(message = "{validation.user.name.required}")
    @Size(max = 100, message = "{validation.user.name.size}")
    private String name;

    @NotBlank(message = "{validation.user.email.required}")
    @Email(message = "{validation.user.email.invalid}")
    private String email;

    @NotBlank(groups = OnCreate.class, message = "{validation.user.password.required}")
    @StrongPassword
    @ToString.Exclude
    private String password;

    @ToString.Exclude
    private String confirmPassword;

    @NotBlank(message = "{validation.client.card.required}")
    @Size(max = 50, message = "{validation.client.card.size}")
    private String card;
}
