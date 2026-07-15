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

        // Sync access for all members
        List<Member> members = memberRepository.findAll();
        for (Member member : members) {
            accessService.syncMemberAccessStatus(member.getId());
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
