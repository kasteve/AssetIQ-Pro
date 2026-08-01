package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.DriverRating;
import com.stevecodes.AssetIQPro.entity.DriverRequest;
import com.stevecodes.AssetIQPro.repository.DriverRatingRepository;
import com.stevecodes.AssetIQPro.service.DriverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/bookings/driver-rating")
public class DriverRatingController {

    private final DriverService driverService;
    private final DriverRatingRepository ratingRepository;

    @GetMapping("/{requestId}")
    public String showRatingPage(@PathVariable Long requestId,
                                 @RequestParam(required = false) String token,
                                 Model model) {
        log.info("Showing driver rating page for request: {}, token: {}", requestId, token);

        // Check if already rated
        if (ratingRepository.findByRequestId(requestId).isPresent()) {
            model.addAttribute("alreadyRated", true);
            model.addAttribute("message", "You have already rated this driver.");
            return "bookings/driver-rating";
        }

        DriverRequest request = driverService.getRequestById(requestId)
                .orElseThrow(() -> new RuntimeException("Driver request not found: " + requestId));

        // If token is provided, validate it
        if (token != null && !token.isEmpty()) {
            // Check if token exists in ratings (for future validation)
            // For now, just use it
            model.addAttribute("token", token);
        } else {
            // Generate a token if not provided
            token = UUID.randomUUID().toString();
            model.addAttribute("token", token);
        }

        String driverName = getDriverName(request.getDriverId());

        model.addAttribute("requestId", requestId);
        model.addAttribute("driverName", driverName);
        model.addAttribute("destination", request.getDestination());
        model.addAttribute("requestTime", request.getRequestTime());
        model.addAttribute("userId", request.getUserId());

        return "bookings/driver-rating";
    }

    @PostMapping("/{requestId}")
    public String submitRating(@PathVariable Long requestId,
                               @RequestParam int rating,
                               @RequestParam(required = false) String feedback,
                               @RequestParam String token,
                               @RequestParam Long userId,
                               RedirectAttributes redirectAttributes) {
        try {
            log.info("Rating submitted for request: {}, rating: {}, token: {}", requestId, rating, token);

            // Check if already rated
            if (ratingRepository.findByRequestId(requestId).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "You have already rated this driver.");
                return "redirect:/bookings/driver-rating/error";
            }

            DriverRequest request = driverService.getRequestById(requestId)
                    .orElseThrow(() -> new RuntimeException("Driver request not found: " + requestId));

            // Create and save rating
            DriverRating driverRating = new DriverRating();
            driverRating.setRequestId(requestId);
            driverRating.setDriverId(request.getDriverId());
            driverRating.setUserId(userId);
            driverRating.setRating(rating);
            driverRating.setFeedback(feedback);
            driverRating.setToken(token);

            ratingRepository.save(driverRating);

            redirectAttributes.addFlashAttribute("success", "Thank you for rating your driver!");
            return "redirect:/bookings/driver-rating/thankyou";
        } catch (Exception e) {
            log.error("Error submitting rating: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to submit rating: " + e.getMessage());
            return "redirect:/bookings/driver-rating/error";
        }
    }

    @GetMapping("/thankyou")
    public String thankYou() {
        return "bookings/driver-rating-thankyou";
    }

    @GetMapping("/error")
    public String error() {
        return "bookings/driver-rating-error";
    }

    private String getDriverName(Long driverId) {
        if (driverId == null) return "Unknown Driver";
        return driverService.getDriverName(driverId);
    }
}