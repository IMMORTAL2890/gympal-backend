package com.gympal.gym;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class TrustedIpService {

    @Autowired
    private TrustedIpRepository trustedIpRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveTrustedIp(GymOwner owner, String ipAddress, String label) {
        // Fetch in-memory to avoid PostgreSQL INET comparison cast issues
        boolean ipExists = trustedIpRepository.findByOwnerId(owner.getId()).stream()
                .anyMatch(ip -> ip.getIpAddress().equalsIgnoreCase(ipAddress));
        
        if (!ipExists) {
            TrustedIp ip = TrustedIp.builder()
                    .owner(owner)
                    .ipAddress(ipAddress)
                    .label(label)
                    .lastSeenAt(Instant.now())
                    .build();
            trustedIpRepository.save(ip);
        }
    }
}
