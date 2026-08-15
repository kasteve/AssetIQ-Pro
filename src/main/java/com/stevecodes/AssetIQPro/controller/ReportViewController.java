package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Category;
import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.repository.CategoryRepository;
import com.stevecodes.AssetIQPro.repository.DepartmentRepository;
import com.stevecodes.AssetIQPro.repository.RoomRepository;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.AppUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/reports")
@PreAuthorize("hasAnyAuthority('VIEW_REPORTS', 'ADMIN')")
public class ReportViewController {

    private final CategoryRepository categoryRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final AppUserService userService;

    @GetMapping
    public String reports(Model model) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        log.info("Loading reports page for user: {}", currentUser.getUsername());

        // Load data for report filters
        List<Category> categories = categoryRepository.findAll();
        model.addAttribute("categories", categories);

        List<Room> rooms = roomRepository.findAll();
        model.addAttribute("rooms", rooms);

        List<AppUser> drivers = userService.getUsersByRole("DRIVER");
        model.addAttribute("drivers", drivers);

        // Add departments for transfer filters
        model.addAttribute("departments", departmentRepository.findAll());

        return "reports/index";
    }
}