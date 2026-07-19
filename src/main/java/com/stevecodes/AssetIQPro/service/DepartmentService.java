package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Department;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final AppUserRepository appUserRepository;

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public Optional<Department> getDepartmentById(Integer id) {
        return departmentRepository.findById(id);
    }

    public Department createDepartment(Department department) {
        return departmentRepository.save(department);
    }

    @Transactional
    public Department updateDepartment(Integer id, String name, Long managerId) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found with id: " + id));

        // Update name
        department.setName(name);

        // Update manager
        if (managerId != null) {
            AppUser manager = appUserRepository.findById(managerId)
                    .orElseThrow(() -> new IllegalArgumentException("Manager not found with id: " + managerId));
            department.setManager(manager);
        } else {
            department.setManager(null);
        }

        return departmentRepository.save(department);
    }

    public void deleteDepartment(Integer id) {
        // Check if department exists before deleting
        if (!departmentRepository.existsById(id)) {
            throw new IllegalArgumentException("Department not found with id: " + id);
        }
        departmentRepository.deleteById(id);
    }

    public boolean existsByName(String name) {
        return departmentRepository.existsByName(name);
    }

    public List<Department> getAllDepartmentsSorted() {
        return departmentRepository.findAllByOrderByNameAsc();
    }
}