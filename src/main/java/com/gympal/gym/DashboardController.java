package com.gympal.gym;

import com.gympal.common.GymOwnerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private GymOwnerContext gymOwnerContext;

    @GetMapping("/stats")
    public ResponseEntity<DashboardService.DashboardStats> getStats(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        UUID ownerId = gymOwnerContext.getGymOwnerId();
        
        // Defaults: start of current month to end of current month
        LocalDate checkFrom = from != null ? from : LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate checkTo = to != null ? to : LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

        return ResponseEntity.ok(dashboardService.getStats(checkFrom, checkTo, ownerId));
    }
}
