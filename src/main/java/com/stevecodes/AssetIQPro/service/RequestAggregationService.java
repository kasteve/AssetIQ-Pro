package com.stevecodes.AssetIQPro.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestAggregationService {

    private final DriverService driverService;
    private final ResourceRequestService resourceRequestService;
    private final BookingService bookingService; // If you have this
    private final InfraRequestService infraRequestService; // If you have this

    public Map<String, Object> getUserRequests(Long userId) {
        Map<String, Object> userRequests = new HashMap<>();

        userRequests.put("driverRequests", driverService.getRequestsByUserId(userId));
        userRequests.put("resourceRequests", resourceRequestService.getResourceRequestsByUserId(userId));
        userRequests.put("roomBookings", bookingService.getBookingsByUserId(userId));
        userRequests.put("infraRequests", infraRequestService.getRequestsByRequesterId(userId));

        return userRequests;
    }
}