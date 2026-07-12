package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Departments", schema = "dbo")
@Data
@NoArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DepartmentId")
    private Integer departmentId;

    @Column(name = "Name", nullable = false, unique = true, columnDefinition = "NVARCHAR(100)")
    private String name;

    @OneToOne
    @JoinColumn(name = "ManagerId")
    private AppUser manager;

    public Department(String name) {
        this.name = name;
    }

    public Department(String name, AppUser manager) {
        this.name = name;
        this.manager = manager;
    }
}