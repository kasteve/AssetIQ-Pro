package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Employees", schema = "dbo")
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"department", "user", "lineManager", "subordinates"})
@ToString(exclude = {"department", "user", "lineManager", "subordinates"})
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EmployeeId")
    private Long employeeId;

    @Column(name = "staff_id", unique = true, nullable = false, length = 50)
    private String staffId;

    @Column(name = "FirstName")
    private String firstName;

    @Column(name = "SurName")
    private String surName;

    @Column(name = "emailAddress")
    private String emailAddress;

    @Column(name = "phoneNumber")
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DepartmentId")
    private Department department;

    @Column(name = "DepartmentId", insertable = false, updatable = false)
    private Integer departmentId;

    @OneToOne(fetch = FetchType.LAZY, mappedBy = "employee")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_manager_id")
    private Employee lineManager;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "lineManager")
    private List<Employee> subordinates = new ArrayList<>();

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