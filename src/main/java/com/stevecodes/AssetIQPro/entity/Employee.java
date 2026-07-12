package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Employees", schema = "dbo")
@Data
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EmployeeId")
    private Long employeeId;

    @Column(name = "FirstName")
    private String firstName;

    @Column(name = "SurName")
    private String surName;

    @Column(name = "emailAddress")
    private String emailAddress;

    @Column(name = "phoneNumber")
    private String phoneNumber;

    @ManyToOne
    @JoinColumn(name = "DepartmentId", nullable = false)
    private Department department;

    @Transient
    private Integer departmentId;

    public String getFullName() {
        if (firstName != null && surName != null) {
            return firstName + " " + surName;
        }
        if (firstName != null) return firstName;
        if (surName != null) return surName;
        return "";
    }

    public String getEmail() {
        return emailAddress;
    }

    public Integer getDepartmentId() {
        if (departmentId != null) return departmentId;
        return department != null ? department.getDepartmentId() : null;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }
}