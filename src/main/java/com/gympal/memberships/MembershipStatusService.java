package com.gympal.memberships;

import com.gympal.access.AccessService;
import com.gympal.common.enums.MembershipStatus;
import com.gympal.members.Member;
import com.gympal.members.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MembershipStatusService {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccessService accessService;

    @Transactional
    public void refreshMembershipStatuses() {
        List<Membership> memberships = membershipRepository.findAll();
        LocalDate today = LocalDate.now();

        for (Membership membership : memberships) {
            MembershipStatus newStatus = calculateStatus(membership, today);
            if (membership.getStatus() != newStatus) {
                membership.setStatus(newStatus);
                membershipRepository.save(membership);
            }
        }

        // Sync access for all members efficiently without N+1
        List<Member> members = memberRepository.findAll();
        java.util.Map<Long, List<Membership>> membershipMap = memberships.stream()
                .collect(Collectors.groupingBy(m -> m.getMember().getId()));

        for (Member member : members) {
            List<Membership> memberMemberships = membershipMap.getOrDefault(member.getId(), java.util.Collections.emptyList());
            boolean hasActiveMembership = memberMemberships.stream().anyMatch(m -> {
                boolean isDateValid = !m.getStartDate().isAfter(today) && !m.getEndDate().isBefore(today);
                boolean isNotUnpaid = !"unpaid".equals(m.getPaymentStatus());
                boolean isPlanActive = m.getPlan() == null || m.getPlan().isActive();
                return isDateValid && isNotUnpaid && isPlanActive;
            });

            if (hasActiveMembership) {
                if (member.getAccessStatus() == com.gympal.common.enums.AccessStatus.blocked) {
                    member.setAccessStatus(com.gympal.common.enums.AccessStatus.allowed);
                    member.setBlockReason(null);
                    member.setBlockedAt(null);
                    memberRepository.save(member);
                }
            } else {
                if (member.getAccessStatus() == com.gympal.common.enums.AccessStatus.allowed) {
                    member.setAccessStatus(com.gympal.common.enums.AccessStatus.blocked);
                    member.setBlockReason("Membership expired");
                    member.setBlockedAt(java.time.Instant.now());
                    memberRepository.save(member);
                }
            }
        }
    }

    private MembershipStatus calculateStatus(Membership m, LocalDate today) {
        if (m.getEndDate().isBefore(today)) {
            return MembershipStatus.expired;
        } else if (!m.getEndDate().isAfter(today.plusDays(7))) {
            return MembershipStatus.expiring_soon;
        } else {
            return MembershipStatus.active;
        }
    }

    // Run at 02:00 AM daily
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledStatusRefresh() {
        refreshMembershipStatuses();
    }
}
