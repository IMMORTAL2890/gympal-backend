package com.gympal.members;

import com.gympal.access.AccessControlLog;
import com.gympal.access.AccessControlLogRepository;
import com.gympal.access.AccessService;
import com.gympal.common.enums.AccessStatus;
import com.gympal.common.enums.MembershipStatus;
import com.gympal.common.exceptions.BadRequestException;
import com.gympal.common.exceptions.NotFoundException;
import com.gympal.memberships.Membership;
import com.gympal.memberships.MembershipRepository;
import com.gympal.payments.PaymentTransaction;
import com.gympal.payments.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MemberService {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private AccessControlLogRepository accessControlLogRepository;

    @Autowired
    private AccessService accessService;

    public List<MemberResponseDto> getMembers(String query, String status, String dues, UUID gymOwnerId) {
        List<Member> members = memberRepository.searchMembers(gymOwnerId, query);
        LocalDate today = LocalDate.now();

        List<MemberResponseDto> responseList = new ArrayList<>();

        for (Member m : members) {
            List<Membership> memberships = membershipRepository.findByMemberIdAndGymOwnerId(m.getId(), gymOwnerId);
            
            // Calculate total outstanding dues
            BigDecimal totalDues = memberships.stream()
                    .map(ms -> ms.getTotalFee().subtract(ms.getDiscountAmount()).subtract(ms.getAmountPaid()))
                    .filter(due -> due.compareTo(BigDecimal.ZERO) > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Determine member status
            String memberStatus = evaluateMemberStatus(memberships, today);

            // Fetch latest membership if any
            Membership latestMembership = memberships.stream()
                    .max(Comparator.comparing(Membership::getEndDate))
                    .orElse(null);

            responseList.add(new MemberResponseDto(m, memberStatus, totalDues, latestMembership));
        }

        // Apply Status Filter
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) {
            responseList = responseList.stream()
                    .filter(dto -> dto.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        // Apply Dues filter (if dues chip clicked, we show members with dues > 0)
        if ("dues".equalsIgnoreCase(status) || "1".equals(dues)) {
            responseList = responseList.stream()
                    .filter(dto -> dto.getTotalDues().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toList());
            
            // Sort by total dues descending (Dues-first sort)
            responseList.sort((a, b) -> b.getTotalDues().compareTo(a.getTotalDues()));
        } else {
            // Default sort: alphabetical
            responseList.sort(Comparator.comparing(a -> a.getMember().getFullName().toLowerCase()));
        }

        return responseList;
    }

    private String evaluateMemberStatus(List<Membership> memberships, LocalDate today) {
        if (memberships.isEmpty()) {
            return "inactive";
        }

        boolean hasActive = memberships.stream().anyMatch(m -> {
            boolean isDateValid = !m.getStartDate().isAfter(today) && !m.getEndDate().isBefore(today);
            boolean isNotUnpaid = !"unpaid".equals(m.getPaymentStatus());
            return isDateValid && isNotUnpaid;
        });

        if (hasActive) {
            boolean hasExpiringSoon = memberships.stream().anyMatch(m -> {
                boolean isDateValid = !m.getStartDate().isAfter(today) && !m.getEndDate().isBefore(today);
                boolean isNotUnpaid = !"unpaid".equals(m.getPaymentStatus());
                boolean isExpiring = !m.getEndDate().isAfter(today.plusDays(7));
                return isDateValid && isNotUnpaid && isExpiring;
            });
            return hasExpiringSoon ? "expiring" : "active";
        }

        boolean hasExpired = memberships.stream().allMatch(m -> m.getEndDate().isBefore(today));
        if (hasExpired) {
            return "expired";
        }

        return "inactive";
    }

    @Transactional
    public Member createMember(MemberCreateDto dto, UUID gymOwnerId) {
        if (dto.getFullName() == null || dto.getFullName().isBlank()) {
            throw new BadRequestException("Full name is required");
        }
        if (dto.getMobileNumber() == null || !dto.getMobileNumber().matches("^[0-9+\\-\\s]{7,15}$")) {
            throw new BadRequestException("Mobile number must be between 7 and 15 digits");
        }
        if (memberRepository.existsByGymOwnerIdAndMobileNumber(gymOwnerId, dto.getMobileNumber())) {
            throw new BadRequestException("A member with this mobile number is already registered");
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            if (!dto.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                throw new BadRequestException("Invalid email format");
            }
        }
        if (dto.getBiometricUid() != null && !dto.getBiometricUid().isBlank()) {
            if (memberRepository.findByGymOwnerIdAndBiometricUid(gymOwnerId, dto.getBiometricUid()).isPresent()) {
                throw new BadRequestException("This Biometric UID is already assigned to another member");
            }
        }
        if (dto.getJoinedDate() != null && dto.getJoinedDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Joined date cannot be in the future");
        }
        if (dto.getDateOfBirth() != null) {
            if (dto.getDateOfBirth().isAfter(LocalDate.now())) {
                throw new BadRequestException("Date of birth cannot be in the future");
            }
            if (dto.getDateOfBirth().isBefore(LocalDate.now().minusYears(100))) {
                throw new BadRequestException("Invalid date of birth");
            }
        }

        Member member = Member.builder()
                .fullName(dto.getFullName())
                .mobileNumber(dto.getMobileNumber())
                .email(dto.getEmail())
                .dateOfBirth(dto.getDateOfBirth())
                .address(dto.getAddress())
                .photoUrl(dto.getPhotoUrl())
                .joinedDate(dto.getJoinedDate() != null ? dto.getJoinedDate() : LocalDate.now())
                .biometricUid(dto.getBiometricUid())
                .accessStatus(AccessStatus.allowed)
                .gymOwnerId(gymOwnerId)
                .build();

        return memberRepository.save(member);
    }

    public MemberDetailDto getMemberDetail(Long id, UUID gymOwnerId) {
        Member member = memberRepository.findByIdAndGymOwnerId(id, gymOwnerId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + id));

        List<Membership> memberships = membershipRepository.findByMemberIdAndGymOwnerId(id, gymOwnerId);
        List<PaymentTransaction> payments = paymentTransactionRepository.findByMemberIdAndGymOwnerId(id, gymOwnerId);
        List<AccessControlLog> accessLogs = accessControlLogRepository.findByMemberIdAndGymOwnerId(id, gymOwnerId);

        // Find active/latest membership for Fee Summary
        LocalDate today = LocalDate.now();
        Membership activeMembership = memberships.stream()
                .filter(m -> !m.getStartDate().isAfter(today) && !m.getEndDate().isBefore(today))
                .findFirst()
                .orElse(null);

        // If no active, take the latest overall
        if (activeMembership == null && !memberships.isEmpty()) {
            activeMembership = memberships.stream()
                    .max(Comparator.comparing(Membership::getEndDate))
                    .orElse(null);
        }

        FeeSummaryDto feeSummary = null;
        if (activeMembership != null) {
            BigDecimal netPayable = activeMembership.getTotalFee().subtract(activeMembership.getDiscountAmount());
            if (netPayable.compareTo(BigDecimal.ZERO) < 0) {
                netPayable = BigDecimal.ZERO;
            }
            BigDecimal due = netPayable.subtract(activeMembership.getAmountPaid());
            if (due.compareTo(BigDecimal.ZERO) < 0) {
                due = BigDecimal.ZERO;
            }

            feeSummary = new FeeSummaryDto(
                    activeMembership.getTotalFee(),
                    activeMembership.getDiscountAmount(),
                    netPayable,
                    activeMembership.getAmountPaid(),
                    due,
                    activeMembership.getId()
            );
        }

        // Sort details chronologically
        memberships.sort((a, b) -> b.getStartDate().compareTo(a.getStartDate()));
        payments.sort((a, b) -> b.getPaymentDate().compareTo(a.getPaymentDate()));
        accessLogs.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        return new MemberDetailDto(member, memberships, payments, accessLogs, feeSummary);
    }

    @Transactional
    public Member updateMember(Long id, MemberUpdateDto dto, UUID gymOwnerId) {
        Member member = memberRepository.findByIdAndGymOwnerId(id, gymOwnerId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + id));

        if (dto.getFullName() != null) {
            if (dto.getFullName().isBlank()) {
                throw new BadRequestException("Full name cannot be empty");
            }
            member.setFullName(dto.getFullName());
        }
        if (dto.getMobileNumber() != null) {
            if (!dto.getMobileNumber().matches("^[0-9+\\-\\s]{7,15}$")) {
                throw new BadRequestException("Mobile number must be between 7 and 15 digits");
            }
            if (!dto.getMobileNumber().equals(member.getMobileNumber())) {
                if (memberRepository.existsByGymOwnerIdAndMobileNumber(gymOwnerId, dto.getMobileNumber())) {
                    throw new BadRequestException("Another member is already registered with this mobile number");
                }
            }
            member.setMobileNumber(dto.getMobileNumber());
        }
        if (dto.getEmail() != null) {
            if (!dto.getEmail().isBlank()) {
                if (!dto.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                    throw new BadRequestException("Invalid email format");
                }
            }
            member.setEmail(dto.getEmail());
        }
        if (dto.getDateOfBirth() != null) {
            if (dto.getDateOfBirth().isAfter(LocalDate.now())) {
                throw new BadRequestException("Date of birth cannot be in the future");
            }
            if (dto.getDateOfBirth().isBefore(LocalDate.now().minusYears(100))) {
                throw new BadRequestException("Invalid date of birth");
            }
            member.setDateOfBirth(dto.getDateOfBirth());
        }
        if (dto.getAddress() != null) {
            member.setAddress(dto.getAddress());
        }
        if (dto.getPhotoUrl() != null) {
            member.setPhotoUrl(dto.getPhotoUrl());
        }
        if (dto.getJoinedDate() != null) {
            if (dto.getJoinedDate().isAfter(LocalDate.now())) {
                throw new BadRequestException("Joined date cannot be in the future");
            }
            member.setJoinedDate(dto.getJoinedDate());
        }
        if (dto.getBiometricUid() != null) {
            if (!dto.getBiometricUid().isBlank() && !dto.getBiometricUid().equals(member.getBiometricUid())) {
                if (memberRepository.findByGymOwnerIdAndBiometricUid(gymOwnerId, dto.getBiometricUid()).isPresent()) {
                    throw new BadRequestException("This Biometric UID is already assigned to another member");
                }
            }
            member.setBiometricUid(dto.getBiometricUid());
        }
        
        return memberRepository.save(member);
    }

    @Transactional
    public Member updateAccess(Long id, AccessUpdateDto dto, UUID gymOwnerId) {
        accessService.manualBlockUnblock(
                id, 
                dto.getAction().equalsIgnoreCase("block") ? AccessStatus.blocked : AccessStatus.allowed,
                dto.getReason(),
                "owner",
                gymOwnerId
        );
        return memberRepository.findByIdAndGymOwnerId(id, gymOwnerId).orElseThrow();
    }

    // DTO Helper Classes
    public static class MemberCreateDto {
        private String fullName;
        private String mobileNumber;
        private String email;
        private LocalDate dateOfBirth;
        private String address;
        private String photoUrl;
        private LocalDate joinedDate;
        private String biometricUid;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getMobileNumber() { return mobileNumber; }
        public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public LocalDate getJoinedDate() { return joinedDate; }
        public void setJoinedDate(LocalDate joinedDate) { this.joinedDate = joinedDate; }
        public String getBiometricUid() { return biometricUid; }
        public void setBiometricUid(String biometricUid) { this.biometricUid = biometricUid; }
    }

    public static class MemberUpdateDto {
        private String fullName;
        private String mobileNumber;
        private String email;
        private LocalDate dateOfBirth;
        private String address;
        private String photoUrl;
        private LocalDate joinedDate;
        private String biometricUid;

        public String getFullName() { return fullName; }
        public String getMobileNumber() { return mobileNumber; }
        public String getEmail() { return email; }
        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public String getAddress() { return address; }
        public String getPhotoUrl() { return photoUrl; }
        public LocalDate getJoinedDate() { return joinedDate; }
        public String getBiometricUid() { return biometricUid; }
    }

    public static class AccessUpdateDto {
        private String action; // block, unblock
        private String reason;
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class MemberResponseDto {
        private Member member;
        private String status;
        private BigDecimal totalDues;
        private Membership latestMembership;

        public MemberResponseDto(Member member, String status, BigDecimal totalDues, Membership latestMembership) {
            this.member = member;
            this.status = status;
            this.totalDues = totalDues;
            this.latestMembership = latestMembership;
        }
        public Member getMember() { return member; }
        public String getStatus() { return status; }
        public BigDecimal getTotalDues() { return totalDues; }
        public Membership getLatestMembership() { return latestMembership; }
    }

    public static class MemberDetailDto {
        private Member member;
        private List<Membership> memberships;
        private List<PaymentTransaction> payments;
        private List<AccessControlLog> accessLogs;
        private FeeSummaryDto activeFeeSummary;

        public MemberDetailDto(Member member, List<Membership> memberships, List<PaymentTransaction> payments, List<AccessControlLog> accessLogs, FeeSummaryDto activeFeeSummary) {
            this.member = member;
            this.memberships = memberships;
            this.payments = payments;
            this.accessLogs = accessLogs;
            this.activeFeeSummary = activeFeeSummary;
        }
        public Member getMember() { return member; }
        public List<Membership> getMemberships() { return memberships; }
        public List<PaymentTransaction> getPayments() { return payments; }
        public List<AccessControlLog> getAccessLogs() { return accessLogs; }
        public FeeSummaryDto getActiveFeeSummary() { return activeFeeSummary; }
    }

    public static class FeeSummaryDto {
        private BigDecimal totalFee;
        private BigDecimal discount;
        private BigDecimal netPayable;
        private BigDecimal paid;
        private BigDecimal due;
        private Long activeMembershipId;

        public FeeSummaryDto(BigDecimal totalFee, BigDecimal discount, BigDecimal netPayable, BigDecimal paid, BigDecimal due, Long activeMembershipId) {
            this.totalFee = totalFee;
            this.discount = discount;
            this.netPayable = netPayable;
            this.paid = paid;
            this.due = due;
            this.activeMembershipId = activeMembershipId;
        }
        public BigDecimal getTotalFee() { return totalFee; }
        public BigDecimal getDiscount() { return discount; }
        public BigDecimal getNetPayable() { return netPayable; }
        public BigDecimal getPaid() { return paid; }
        public BigDecimal getDue() { return due; }
        public Long getActiveMembershipId() { return activeMembershipId; }
    }
}
