package com.gympal.attendance;

import com.gympal.common.enums.PunchSource;
import com.gympal.common.enums.PunchType;
import com.gympal.members.Member;
import com.gympal.members.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/iclock")
public class AdmsController {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BiometricDeviceRepository biometricDeviceRepository;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ADMS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 1. GET /iclock/cdata
     * Handshake and option sync between device and cloud.
     */
    @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handleHandshake(
            @RequestParam(value = "SN") String serialNumber,
            @RequestParam(value = "options", required = false) String options) {
        
        System.out.println("[ADMS Handshake] Device SN connected: " + serialNumber);
        
        // Verify device is registered in FitTrack
        Optional<BiometricDevice> deviceOpt = biometricDeviceRepository.findAll().stream()
                .filter(d -> serialNumber.equals(d.getDeviceSerial()))
                .findFirst();
                
        if (deviceOpt.isEmpty()) {
            System.out.println("[ADMS Handshake] Unregistered device serial attempted: " + serialNumber);
            return ResponseEntity.status(401).body("Unregistered device: " + serialNumber);
        }

        // Return standard ADMS config commands
        String responseBody = "RegistryCode=FitTrack\n" +
                "Delay=15\n" +
                "ErrorDelay=60\n" +
                "TransTimes=00:00;23:59\n" +
                "TransInterval=15\n" +
                "Realtime=1\n" +
                "Encrypt=0\n";
        return ResponseEntity.ok(responseBody);
    }

    /**
     * 2. POST /iclock/cdata
     * Receives attendance log chunks (ATTLOG) directly from the device.
     */
    @PostMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receiveDeviceLogs(
            @RequestParam(value = "SN") String serialNumber,
            @RequestParam(value = "table") String table,
            @RequestBody String body) {

        System.out.println("[ADMS POST Data] Device SN: " + serialNumber + ", table: " + table);
        
        Optional<BiometricDevice> deviceOpt = biometricDeviceRepository.findAll().stream()
                .filter(d -> serialNumber.equals(d.getDeviceSerial()))
                .findFirst();

        if (deviceOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Return=-1");
        }

        BiometricDevice device = deviceOpt.get();
        UUID gymOwnerId = device.getGymOwnerId();

        // Process only attendance log uploads (ATTLOG)
        if ("ATTLOG".equalsIgnoreCase(table)) {
            String[] lines = body.split("\\R");
            int accepted = 0;
            
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                
                // Format: [User-PIN]\t[Timestamp]\t[VerifyMode]\t[InOutMode]\t[WorkCode]...
                String[] parts = line.split("\\t");
                if (parts.length >= 2) {
                    String biometricUid = parts[0].trim();
                    String timestampStr = parts[1].trim();
                    
                    try {
                        // Parse local time as IST and convert to UTC Instant
                        LocalDateTime localDateTime = LocalDateTime.parse(timestampStr, ADMS_DATE_FORMAT);
                        Instant punchTime = localDateTime.atZone(IST).toInstant();
                        
                        // Map user PIN to a member
                        Optional<Member> memberOpt = memberRepository.findByGymOwnerIdAndBiometricUid(gymOwnerId, biometricUid);
                        if (memberOpt.isPresent()) {
                            Member member = memberOpt.get();
                            attendanceService.registerPunch(
                                    member.getId(),
                                    biometricUid,
                                    device.getId(),
                                    punchTime,
                                    PunchType.unknown,
                                    PunchSource.biometric,
                                    "ADMS Direct Sync",
                                    gymOwnerId
                            );
                            accepted++;
                        }
                    } catch (Exception e) {
                        System.err.println("[ADMS Parsing Error] Could not process line '" + line + "': " + e.getMessage());
                    }
                }
            }
            System.out.println("[ADMS Sync Completed] Processed " + accepted + " punches successfully.");
            
            // Update device sync statistics
            device.setLastSyncStatus(com.gympal.common.enums.DeviceSyncStatus.success);
            device.setLastSyncTime(Instant.now());
            device.setLastSyncCount(accepted);
            biometricDeviceRepository.save(device);
            
            return ResponseEntity.ok("OK");
        }

        return ResponseEntity.ok("OK");
    }

    /**
     * 3. GET /iclock/getrequest
     * Machine polls this to check for pending server commands (e.g. remote reboot).
     */
    @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handleCommandPolling(@RequestParam(value = "SN") String serialNumber) {
        // Return empty response (no commands pending)
        return ResponseEntity.ok("OK");
    }

    /**
     * 4. POST /iclock/devicecmd
     * Machine posts execution confirmation results of server commands here.
     */
    @PostMapping(value = "/devicecmd", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receiveCommandConfirmations(
            @RequestParam(value = "SN") String serialNumber,
            @RequestBody String body) {
        return ResponseEntity.ok("OK");
    }
}
