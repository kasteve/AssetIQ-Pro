package com.stevecodes.AssetIQPro.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/vouchers")
@PreAuthorize("hasAnyAuthority('GENERATE_VOUCHERS', 'ADMIN', 'SUPER_ADMIN')")
public class VoucherViewController {

    @GetMapping
    public String vouchers() {
        log.info("Loading vouchers page");
        return "vouchers/list";
    }
}