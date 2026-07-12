package com.stevecodes.AssetIQPro.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/transfers")
public class TransferViewController {

    @GetMapping
    public String transfers() {
        log.info("Loading transfers page");
        return "transfers/list";
    }
}