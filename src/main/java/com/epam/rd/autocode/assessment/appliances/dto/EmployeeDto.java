package com.epam.rd.autocode.assessment.appliances.dto;

import com.epam.rd.autocode.assessment.appliances.validation.PasswordsMatch;
import com.epam.rd.autocode.assessment.appliances.validation.StrongPassword;
import com.epam.rd.autocode.assessment.appliances.validation.UniqueEmail;
import com.epam.rd.autocode.assessment.appliances.validation.UserType;
import com.epam.rd.autocode.assessment.appliances.validation.ValidationGroups.OnCreate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Form object for creating/editing an employee. Password rules are the same as in ClientDto. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@UniqueEmail(type = UserType.EMPLOYEE)
@PasswordsMatch
public class EmployeeDto {

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

    @NotBlank(message = "{validation.employee.department.required}")
    @Size(max = 100, message = "{validation.employee.department.size}")
    private String department;
}
