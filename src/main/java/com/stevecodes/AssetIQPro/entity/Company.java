package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Company")
@Data
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CompanyId")
    private Long companyId;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    public Company(String name) {
        this.name = name;
    }
}