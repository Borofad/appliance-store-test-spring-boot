package com.epam.rd.autocode.assessment.appliances.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Employee extends User {

    @NotBlank(message = "{validation.employee.department.required}")
    @Size(max = 100, message = "{validation.employee.department.size}")
    @Column(nullable = false, length = 100)
    private String department;

    public Employee(Long id, String name, String email, String password, String department) {
        super(id, name, email, password);
        this.department = department;
    }

    @Override
    @Transient
    public String getRole() {
        return "EMPLOYEE";
    }
}