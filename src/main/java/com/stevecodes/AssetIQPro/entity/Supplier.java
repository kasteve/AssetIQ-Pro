package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Suppliers")
@Data
@NoArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SupplierId")
    private Integer supplierId;

    @Column(nullable = false)
    private String name;

    private String contact;
    private String email;
    private String phone;

    @OneToMany(mappedBy = "supplier")
    private List<Asset> assets = new ArrayList<>();

    public Supplier(String name) {
        this.name = name;
    }

    // Explicit getters
    public Integer getSupplierId() {
        return supplierId;
    }

    public String getName() {
        return name;
    }

    public String getContact() {
        return contact;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public List<Asset> getAssets() {
        return assets;
    }

    // Explicit setters
    public void setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setAssets(List<Asset> assets) {
        this.assets = assets;
    }
}