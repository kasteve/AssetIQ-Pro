package com.stevecodes.AssetIQPro.security;

/**
 * Permission constants for the application.
 * Use these in @PreAuthorize annotations and permission checks.
 */
public final class Permissions {

    // ============================================
    // Report Permissions
    // ============================================
    public static final String VIEW_REPORTS = "VIEW_REPORTS";
    public static final String DOWNLOAD_REPORTS = "DOWNLOAD_REPORTS";

    // ============================================
    // Asset Permissions
    // ============================================
    public static final String ASSET_VIEW = "ASSET_VIEW";
    public static final String ASSET_CREATE = "ASSET_CREATE";
    public static final String ASSET_EDIT = "ASSET_EDIT";
    public static final String ASSET_MOVE = "ASSET_MOVE";
    public static final String ASSET_ASSIGN = "ASSET_ASSIGN";
    public static final String DELETE_ASSETS = "DELETE_ASSETS";
    public static final String MANAGE_WARRANTY = "MANAGE_WARRANTY";
    public static final String MANAGE_EOL = "MANAGE_EOL";

    // ============================================
    // Transfer Permissions
    // ============================================
    public static final String TRANSFER_VIEW = "TRANSFER_VIEW";
    public static final String TRANSFER_CREATE = "TRANSFER_CREATE";
    public static final String TRANSFER_BULK_EXPORT = "TRANSFER_BULK_EXPORT";

    // ============================================
    // Approval Permissions
    // ============================================
    public static final String APPROVE_LM = "APPROVE_LM";
    public static final String APPROVE_INFRA = "APPROVE_INFRA";
    public static final String APPROVE_FINANCE = "APPROVE_FINANCE";
    public static final String APPROVE_INFRA_REQUESTS = "APPROVE_INFRA_REQUESTS";

    // ============================================
    // Request Permissions
    // ============================================
    public static final String INFRA_REQUEST_VIEW = "INFRA_REQUEST_VIEW";
    public static final String INFRA_REQUEST_CREATE = "INFRA_REQUEST_CREATE";
    public static final String INFRA_REQUEST_APPROVE = "INFRA_REQUEST_APPROVE";
    public static final String INFRA_REQUEST_ATTACH_QUOTATION = "INFRA_REQUEST_ATTACH_QUOTATION";

    public static final String RESOURCE_REQUEST_VIEW = "RESOURCE_REQUEST_VIEW";
    public static final String RESOURCE_REQUEST_CREATE = "RESOURCE_REQUEST_CREATE";
    public static final String RESOURCE_REQUEST_APPROVE = "RESOURCE_REQUEST_APPROVE";

    // ============================================
    // User Management Permissions
    // ============================================
    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_EDIT = "USER_EDIT";
    public static final String USER_DISABLE = "USER_DISABLE";
    public static final String USER_LOCK = "USER_LOCK";
    public static final String CREATE_USERS = "CREATE_USERS";
    public static final String RESET_PASSWORDS = "RESET_PASSWORDS";
    public static final String MANAGE_ROLES = "MANAGE_ROLES";
    public static final String MANAGE_CONFIG = "MANAGE_CONFIG";

    // ============================================
    // Transaction Permissions
    // ============================================
    public static final String VIEW_ALL_TRANSACTIONS = "VIEW_ALL_TRANSACTIONS";
    public static final String VIEW_OWN_TRANSACTIONS = "VIEW_OWN_TRANSACTIONS";

    // ============================================
    // Booking Permissions
    // ============================================
    public static final String MANAGE_BOOKINGS = "MANAGE_BOOKINGS";
    public static final String ROOM_BOOK = "ROOM_BOOK";
    public static final String ROOM_CANCEL = "ROOM_CANCEL";
    public static final String ROOM_VIEW_ALL = "ROOM_VIEW_ALL";

    // ============================================
    // Driver Permissions
    // ============================================
    public static final String DRIVER_VIEW = "DRIVER_VIEW";
    public static final String DRIVER_APPROVE = "DRIVER_APPROVE";
    public static final String DRIVER_REQUEST = "DRIVER_REQUEST";
    public static final String DRIVER_CANCEL = "DRIVER_CANCEL";

    public static final String CAB_REQUEST_VIEW = "CAB_REQUEST_VIEW";
    public static final String CAB_REQUEST_APPROVE = "CAB_REQUEST_APPROVE";

    // ============================================
    // Employee Permissions
    // ============================================
    public static final String EMPLOYEE_VIEW = "EMPLOYEE_VIEW";
    public static final String EMPLOYEE_CREATE = "EMPLOYEE_CREATE";
    public static final String EMPLOYEE_EDIT = "EMPLOYEE_EDIT";

    // ============================================
    // Voucher Permissions
    // ============================================
    public static final String GENERATE_VOUCHERS = "GENERATE_VOUCHERS";
    public static final String GENERATE_MULTIPLE_VOUCHERS = "GENERATE_MULTIPLE_VOUCHERS";

    // ============================================
    // Audit Permissions
    // ============================================
    public static final String VIEW_AUDIT = "VIEW_AUDIT";

    // ============================================
    // Admin Permissions
    // ============================================
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    // ============================================
    // Inventory/Stock Permissions
    // ============================================
    public static final String MANAGE_INVENTORY = "MANAGE_INVENTORY";

    private Permissions() {
        // Private constructor to prevent instantiation
    }
}