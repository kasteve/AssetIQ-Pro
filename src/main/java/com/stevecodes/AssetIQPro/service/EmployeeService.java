package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Employee;
import com.stevecodes.AssetIQPro.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    public List<Employee> getAllEmployees() { return employeeRepository.findAll(); }
    public Employee getEmployeeById(Long id) { return employeeRepository.findById(id).orElse(null); }
}