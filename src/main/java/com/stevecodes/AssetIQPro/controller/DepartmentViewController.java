package com.stevecodes.AssetIQPro.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// DepartmentViewController.java
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/departments")
public class DepartmentViewController {
    @GetMapping
    public String departments() {
        return "admin/departments";
    }
}
