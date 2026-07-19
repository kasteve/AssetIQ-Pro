package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Supplier;
import com.stevecodes.AssetIQPro.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    public Supplier getSupplierById(Integer id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
    }

    public Supplier createSupplier(Supplier supplier) {
        return supplierRepository.save(supplier);
    }

    public void deleteSupplier(Integer id) {
        supplierRepository.deleteById(id);
    }

    public Supplier updateSupplier(Integer id, String name, String contact, String email, String phone) {
        Supplier supplier = getSupplierById(id);
        supplier.setName(name);
        supplier.setContact(contact);
        supplier.setEmail(email);
        supplier.setPhone(phone);
        return supplierRepository.save(supplier);
    }
}