package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "Departments", schema = "dbo")
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"manager"})
@ToString(exclude = {"manager"})
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DepartmentId")
    private Integer departmentId;

    @Column(name = "Name", nullable = false, unique = true, columnDefinition = "NVARCHAR(100)")
    private String name;

    @OneToOne(fetch = FetchType.LAZY)
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