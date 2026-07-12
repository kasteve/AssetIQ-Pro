package com.stevecodes.AssetIQPro.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/infra-requests")
public class InfraRequestViewController {

    @GetMapping
    public String infraRequests() {
        log.info("Loading infrastructure requests page");
        return "infra-requests/list";
    }
}