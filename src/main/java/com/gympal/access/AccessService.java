package com.gympal.access;

import com.gympal.common.exceptions.NotFoundException;

import com.gympal.common.enums.AccessAction;
import com.gympal.common.enums.AccessStatus;
import com.gympal.common.enums.MembershipStatus;
import com.gympal.members.Member;
import com.gympal.members.MemberRepository;
import com.gympal.memberships.Membership;
import com.gympal.memberships.MembershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AccessService {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private AccessControlLogRepository accessControlLogRepository;

    @Transactional
    public void syncMemberAccessStatus(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        List<Membership> memberships = membershipRepository.findByMemberIdAndGymOwnerId(memberId, member.getGymOwnerId());
        LocalDate today = LocalDate.now();

        boolean hasActiveMembership = memberships.stream().anyMatch(m -> {
            boolean isDateValid = !m.getStartDate().isAfter(today) && !m.getEndDate().isBefore(today);
            boolean isNotUnpaid = !"unpaid".equals(m.getPaymentStatus());
            boolean isPlanActive = m.getPlan() == null || m.getPlan().isActive();
            return isDateValid && isNotUnpaid && isPlanActive;
        });

        if (hasActiveMembership) {
            // Member has an active membership, check if they need to be unblocked
            if (member.getAccessStatus() == AccessStatus.blocked) {
                // If they were blocked due to "Membership expired" or we are unblocking them because they renewed
                member.setAccessStatus(AccessStatus.allowed);
                member.setBlockReason(null);
                member.setBlockedAt(null);
                memberRepository.save(member);

                AccessControlLog log = AccessControlLog.builder()
                        .member(member)
                        .action(AccessAction.enabled)
                        .reason("Membership active/renewed")
                        .performedBy("auto")
                        .gymOwnerId(member.getGymOwnerId())
                        .build();
                accessControlLogRepository.save(log);
            }
        } else {
            // No active membership, block them if they are allowed
            if (member.getAccessStatus() == AccessStatus.allowed) {
                member.setAccessStatus(AccessStatus.blocked);
                member.setBlockReason("Membership expired");
                member.setBlockedAt(Instant.now());
                memberRepository.save(member);

                AccessControlLog log = AccessControlLog.builder()
                        .member(member)
                        .action(AccessAction.disabled)
                        .reason("Membership expired")
                        .performedBy("auto")
                        .gymOwnerId(member.getGymOwnerId())
                        .build();
                accessControlLogRepository.save(log);
            }
        }
    }

    @Transactional
    public void manualBlockUnblock(Long memberId, AccessStatus targetStatus, String reason, String performedBy, UUID gymOwnerId) {
        Member member = memberRepository.findByIdAndGymOwnerId(memberId, gymOwnerId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        if (member.getAccessStatus() == targetStatus) {
            return; // no change
        }

        if (targetStatus == AccessStatus.blocked) {
            member.setAccessStatus(AccessStatus.blocked);
            member.setBlockReason(reason != null ? reason : "Manually blocked");
            member.setBlockedAt(Instant.now());
            memberRepository.save(member);

            AccessControlLog log = AccessControlLog.builder()
                    .member(member)
                    .action(AccessAction.disabled)
                    .reason(reason != null ? reason : "Manually blocked")
                    .performedBy(performedBy != null ? performedBy : "owner")
                    .gymOwnerId(gymOwnerId)
                    .build();
            accessControlLogRepository.save(log);
        } else {
            member.setAccessStatus(AccessStatus.allowed);
            member.setBlockReason(null);
            member.setBlockedAt(null);
            memberRepository.save(member);

            AccessControlLog log = AccessControlLog.builder()
                    .member(member)
                    .action(AccessAction.enabled)
                    .reason(reason != null ? reason : "Manually unblocked")
                    .performedBy(performedBy != null ? performedBy : "owner")
                    .gymOwnerId(gymOwnerId)
                    .build();
            accessControlLogRepository.save(log);
        }
    }
}
