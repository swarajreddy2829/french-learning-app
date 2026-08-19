package com.example.frenchlearning.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "role")
public class Role {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 20)
    private RoleName name;

    protected Role() {}

    Role(short id, RoleName name) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    public Short getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }
}
