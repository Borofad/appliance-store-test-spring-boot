package com.epam.rd.autocode.assessment.appliances.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "client")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Client extends User {

    @NotBlank(message = "{validation.client.card.required}")
    @Size(max = 50, message = "{validation.client.card.size}")
    @Column(nullable = false, length = 50)
    private String card;

    public Client(Long id, String name, String email, String password, String card) {
        super(id, name, email, password);
        this.card = card;
    }

    @Override
    @Transient
    public String getRole() {
        return "CLIENT";
    }
}