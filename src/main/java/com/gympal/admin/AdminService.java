package com.gympal.admin;

import com.gympal.common.enums.MembershipStatus;
import com.gympal.common.exceptions.NotFoundException;
import com.gympal.gym.GymOwner;
import com.gympal.gym.GymOwnerRepository;
import com.gympal.gym.TrustedIp;
import com.gympal.gym.TrustedIpRepository;
import com.gympal.members.Member;
import com.gympal.members.MemberRepository;
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
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired
    private GymOwnerRepository gymOwnerRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private TrustedIpRepository trustedIpRepository;

    public List<GymSummaryDto> getPlatformGymSummary() {
        List<GymOwner> gyms = gymOwnerRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

        List<Member> allMembers = memberRepository.findAll();
        List<PaymentTransaction> allTxs = paymentTransactionRepository.findAll();
        List<TrustedIp> allIps = trustedIpRepository.findAll();

        Map<UUID, Long> memberCountMap = allMembers.stream().collect(Collectors.groupingBy(Member::getGymOwnerId, Collectors.counting()));
        Map<UUID, List<PaymentTransaction>> txMap = allTxs.stream().collect(Collectors.groupingBy(PaymentTransaction::getGymOwnerId));
        Map<UUID, List<TrustedIp>> ipMap = allIps.stream().collect(Collectors.groupingBy(ip -> ip.getOwner().getId()));

        List<GymSummaryDto> summaries = new ArrayList<>();

        for (GymOwner gym : gyms) {
            UUID gymId = gym.getId();
            long memberCount = memberCountMap.getOrDefault(gymId, 0L);

            List<PaymentTransaction> txs = txMap.getOrDefault(gymId, Collections.emptyList());
            
            BigDecimal allTimeRevenue = txs.stream()
                    .map(PaymentTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal thisMonthRevenue = txs.stream()
                    .filter(tx -> !tx.getPaymentDate().isBefore(startOfMonth) && !tx.getPaymentDate().isAfter(endOfMonth))
                    .map(PaymentTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Last active time from trusted IPs
            List<TrustedIp> ips = ipMap.getOrDefault(gymId, Collections.emptyList());
            Instant lastActive = ips.stream()
                    .map(TrustedIp::getLastSeenAt)
                    .filter(Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(gym.getCreatedAt());

            summaries.add(new GymSummaryDto(
                    gymId,
                    gym.getGymName(),
                    gym.getOwnerName(),
                    gym.getMobileNumber(),
                    memberCount,
                    allTimeRevenue,
                    thisMonthRevenue,
                    gym.getCreatedAt(),
                    lastActive,
                    gym.getStatus(),
                    gym.getSubscriptionPlan()
            ));
        }

        return summaries;
    }

    public GymDetailDto getPlatformGymDetail(UUID gymId) {
        GymOwner gym = gymOwnerRepository.findById(gymId)
                .orElseThrow(() -> new NotFoundException("Gym not found: " + gymId));

        List<Member> members = memberRepository.findByGymOwnerId(gymId);
        List<Membership> memberships = membershipRepository.findByGymOwnerId(gymId);
        List<PaymentTransaction> txs = paymentTransactionRepository.findByGymOwnerId(gymId);

        // Member stats
        long totalMembers = members.size();
        LocalDate today = LocalDate.now();
        long activeMembers = memberships.stream()
                .filter(m -> m.getStatus() == MembershipStatus.active || m.getStatus() == MembershipStatus.expiring_soon)
                .map(m -> m.getMember().getId())
                .distinct()
                .count();

        BigDecimal allTimeRevenue = txs.stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Trailing 12 months revenue
        List<MonthlyRevenueDto> monthlyRevenue = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        for (int i = 0; i < 12; i++) {
            YearMonth targetMonth = currentMonth.minusMonths(i);
            LocalDate start = targetMonth.atDay(1);
            LocalDate end = targetMonth.atEndOfMonth();

            BigDecimal revenue = txs.stream()
                    .filter(tx -> !tx.getPaymentDate().isBefore(start) && !tx.getPaymentDate().isAfter(end))
                    .map(PaymentTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            monthlyRevenue.add(new MonthlyRevenueDto(
                    targetMonth.getMonth().name() + " " + targetMonth.getYear(),
                    revenue
            ));
        }
        Collections.reverse(monthlyRevenue); // display chronologically

        // List members with statuses
        List<AdminMemberDto> memberListDto = members.stream().map(m -> {
            Membership latest = memberships.stream()
                    .filter(ms -> ms.getMember().getId().equals(m.getId()))
                    .max(Comparator.comparing(Membership::getEndDate))
                    .orElse(null);
            
            return new AdminMemberDto(
                    m.getId(),
                    m.getFullName(),
                    m.getMobileNumber(),
                    m.getJoinedDate(),
                    latest != null && latest.getPlan() != null ? latest.getPlan().getPlanName() : "No Plan",
                    latest != null ? latest.getStatus().name() : "Expired"
            );
        }).collect(Collectors.toList());

        return new GymDetailDto(
                gym.getId(),
                gym.getGymName(),
                gym.getOwnerName(),
                gym.getMobileNumber(),
                totalMembers,
                activeMembers,
                allTimeRevenue,
                monthlyRevenue,
                memberListDto,
                gym.getStatus(),
                gym.getSubscriptionPlan()
        );
    }

    @Transactional
    public GymOwner updateGymStatus(UUID gymId, String status, String subscriptionPlan) {
        GymOwner gym = gymOwnerRepository.findById(gymId)
                .orElseThrow(() -> new NotFoundException("Gym not found: " + gymId));

        if (status != null && !status.isEmpty()) {
            gym.setStatus(status);
        }
        if (subscriptionPlan != null && !subscriptionPlan.isEmpty()) {
            gym.setSubscriptionPlan(subscriptionPlan);
        }

        return gymOwnerRepository.save(gym);
    }

    public AdminDashboardStatsDto getDashboardStats() {
        long totalGyms = gymOwnerRepository.count();
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        
        long previousGymsCount = gymOwnerRepository.findAll().stream()
                .filter(g -> g.getCreatedAt().isBefore(startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .count();
        double growthPct = 0.0;
        if (previousGymsCount > 0) {
            long newGymsThisMonth = totalGyms - previousGymsCount;
            growthPct = (newGymsThisMonth * 100.0) / previousGymsCount;
        } else if (totalGyms > 0) {
            growthPct = 100.0;
        }

        long activeMembers = membershipRepository.findAll().stream()
                .filter(m -> m.getStatus() == MembershipStatus.active || m.getStatus() == MembershipStatus.expiring_soon)
                .map(m -> m.getMember().getId())
                .distinct()
                .count();

        List<PaymentTransaction> allTxs = paymentTransactionRepository.findAll();
        BigDecimal totalRevenue = allTxs.stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long newGyms = gymOwnerRepository.findAll().stream()
                .filter(g -> !g.getCreatedAt().isBefore(startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .count();
        long newUsers = memberRepository.findAll().stream()
                .filter(m -> !m.getJoinedDate().isBefore(startOfMonth))
                .count();

        List<MonthlyRevenueDto> revenueTrend = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        for (int i = 0; i < 12; i++) {
            YearMonth targetMonth = currentMonth.minusMonths(i);
            LocalDate start = targetMonth.atDay(1);
            LocalDate end = targetMonth.atEndOfMonth();

            BigDecimal monthlyRev = allTxs.stream()
                    .filter(tx -> !tx.getPaymentDate().isBefore(start) && !tx.getPaymentDate().isAfter(end))
                    .map(PaymentTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            revenueTrend.add(new MonthlyRevenueDto(
                    targetMonth.getMonth().name().substring(0, 3) + " " + targetMonth.getYear(),
                    monthlyRev
            ));
        }
        Collections.reverse(revenueTrend);

        Map<UUID, BigDecimal> gymRevenueMap = allTxs.stream()
                .collect(Collectors.groupingBy(
                        PaymentTransaction::getGymOwnerId,
                        Collectors.reducing(BigDecimal.ZERO, PaymentTransaction::getAmount, BigDecimal::add)
                ));

        List<TopGymDto> topGyms = gymOwnerRepository.findAll().stream()
                .map(g -> new TopGymDto(
                        g.getId(),
                        g.getGymName(),
                        g.getOwnerName(),
                        gymRevenueMap.getOrDefault(g.getId(), BigDecimal.ZERO)
                ))
                .sorted(Comparator.comparing(TopGymDto::getTotalRevenue).reversed())
                .limit(5)
                .collect(Collectors.toList());

        return new AdminDashboardStatsDto(
                totalGyms,
                growthPct,
                activeMembers,
                totalRevenue,
                newGyms,
                newUsers,
                revenueTrend,
                topGyms
        );
    }

    public List<AdminUserDto> getPlatformUsers(UUID gymId, String status, String plan) {
        List<Member> members = memberRepository.findAll();
        List<Membership> memberships = membershipRepository.findAll();
        List<PaymentTransaction> txs = paymentTransactionRepository.findAll();
        List<GymOwner> gyms = gymOwnerRepository.findAll();
        Map<UUID, String> gymNameMap = gyms.stream().collect(Collectors.toMap(GymOwner::getId, GymOwner::getGymName));

        return members.stream()
                .filter(m -> gymId == null || m.getGymOwnerId().equals(gymId))
                .map(m -> {
                    Membership latest = memberships.stream()
                            .filter(ms -> ms.getMember().getId().equals(m.getId()))
                            .max(Comparator.comparing(Membership::getEndDate))
                            .orElse(null);

                    if (plan != null && !plan.isEmpty()) {
                        if (latest == null || latest.getPlan() == null || !latest.getPlan().getPlanName().equalsIgnoreCase(plan)) {
                            return null;
                        }
                    }

                    String mStatus = latest != null ? latest.getStatus().name() : "expired";
                    if (status != null && !status.isEmpty()) {
                        if (!mStatus.equalsIgnoreCase(status)) {
                            return null;
                        }
                    }

                    LocalDate lastPayDate = txs.stream()
                            .filter(tx -> tx.getMember().getId().equals(m.getId()))
                            .map(PaymentTransaction::getPaymentDate)
                            .max(LocalDate::compareTo)
                            .orElse(null);

                    return new AdminUserDto(
                            m.getId(),
                            m.getFullName(),
                            m.getEmail(),
                            m.getMobileNumber(),
                            m.getGymOwnerId(),
                            gymNameMap.getOrDefault(m.getGymOwnerId(), "Unknown Gym"),
                            mStatus,
                            m.getJoinedDate(),
                            lastPayDate
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public PlatformRevenueDto getPlatformRevenue(LocalDate start, LocalDate end) {
        List<PaymentTransaction> txs = paymentTransactionRepository.findAll();
        List<GymOwner> gyms = gymOwnerRepository.findAll();

        BigDecimal total = txs.stream()
                .filter(tx -> (start == null || !tx.getPaymentDate().isBefore(start)) && (end == null || !tx.getPaymentDate().isAfter(end)))
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<GymRevenueDto> breakdown = gyms.stream().map(g -> {
            BigDecimal rev = txs.stream()
                    .filter(tx -> tx.getGymOwnerId().equals(g.getId()))
                    .filter(tx -> (start == null || !tx.getPaymentDate().isBefore(start)) && (end == null || !tx.getPaymentDate().isAfter(end)))
                    .map(PaymentTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new GymRevenueDto(g.getId(), g.getGymName(), g.getOwnerName(), rev);
        }).collect(Collectors.toList());

        return new PlatformRevenueDto(total, breakdown);
    }

    // DTO Classes
    public static class GymSummaryDto {
        private UUID gymId;
        private String gymName;
        private String ownerName;
        private String mobileNumber;
        private long memberCount;
        private BigDecimal allTimeRevenue;
        private BigDecimal thisMonthRevenue;
        private Instant joinedDate;
        private Instant lastActive;
        private String status;
        private String subscriptionPlan;

        public GymSummaryDto(UUID gymId, String gymName, String ownerName, String mobileNumber, long memberCount, BigDecimal allTimeRevenue, BigDecimal thisMonthRevenue, Instant joinedDate, Instant lastActive, String status, String subscriptionPlan) {
            this.gymId = gymId;
            this.gymName = gymName;
            this.ownerName = ownerName;
            this.mobileNumber = mobileNumber;
            this.memberCount = memberCount;
            this.allTimeRevenue = allTimeRevenue;
            this.thisMonthRevenue = thisMonthRevenue;
            this.joinedDate = joinedDate;
            this.lastActive = lastActive;
            this.status = status;
            this.subscriptionPlan = subscriptionPlan;
        }

        public UUID getGymId() { return gymId; }
        public String getGymName() { return gymName; }
        public String getOwnerName() { return ownerName; }
        public String getMobileNumber() { return mobileNumber; }
        public long getMemberCount() { return memberCount; }
        public BigDecimal getAllTimeRevenue() { return allTimeRevenue; }
        public BigDecimal getThisMonthRevenue() { return thisMonthRevenue; }
        public Instant getJoinedDate() { return joinedDate; }
        public Instant getLastActive() { return lastActive; }
        public String getStatus() { return status; }
        public String getSubscriptionPlan() { return subscriptionPlan; }
    }

    public static class GymDetailDto {
        private UUID gymId;
        private String gymName;
        private String ownerName;
        private String mobileNumber;
        private long totalMembers;
        private long activeMembers;
        private BigDecimal allTimeRevenue;
        private List<MonthlyRevenueDto> monthlyRevenue;
        private List<AdminMemberDto> members;
        private String status;
        private String subscriptionPlan;

        public GymDetailDto(UUID gymId, String gymName, String ownerName, String mobileNumber, long totalMembers, long activeMembers, BigDecimal allTimeRevenue, List<MonthlyRevenueDto> monthlyRevenue, List<AdminMemberDto> members, String status, String subscriptionPlan) {
            this.gymId = gymId;
            this.gymName = gymName;
            this.ownerName = ownerName;
            this.mobileNumber = mobileNumber;
            this.totalMembers = totalMembers;
            this.activeMembers = activeMembers;
            this.allTimeRevenue = allTimeRevenue;
            this.monthlyRevenue = monthlyRevenue;
            this.members = members;
            this.status = status;
            this.subscriptionPlan = subscriptionPlan;
        }

        public UUID getGymId() { return gymId; }
        public String getGymName() { return gymName; }
        public String getOwnerName() { return ownerName; }
        public String getMobileNumber() { return mobileNumber; }
        public long getTotalMembers() { return totalMembers; }
        public long getActiveMembers() { return activeMembers; }
        public BigDecimal getAllTimeRevenue() { return allTimeRevenue; }
        public List<MonthlyRevenueDto> getMonthlyRevenue() { return monthlyRevenue; }
        public List<AdminMemberDto> getMembers() { return members; }
        public String getStatus() { return status; }
        public String getSubscriptionPlan() { return subscriptionPlan; }
    }

    public static class MonthlyRevenueDto {
        private String month;
        private BigDecimal revenue;

        public MonthlyRevenueDto(String month, BigDecimal revenue) {
            this.month = month;
            this.revenue = revenue;
        }
        public String getMonth() { return month; }
        public BigDecimal getRevenue() { return revenue; }
    }

    public static class AdminMemberDto {
        private Long id;
        private String fullName;
        private String mobileNumber;
        private LocalDate joinedDate;
        private String planName;
        private String status;

        public AdminMemberDto(Long id, String fullName, String mobileNumber, LocalDate joinedDate, String planName, String status) {
            this.id = id;
            this.fullName = fullName;
            this.mobileNumber = mobileNumber;
            this.joinedDate = joinedDate;
            this.planName = planName;
            this.status = status;
        }

        public Long getId() { return id; }
        public String getFullName() { return fullName; }
        public String getMobileNumber() { return mobileNumber; }
        public LocalDate getJoinedDate() { return joinedDate; }
        public String getPlanName() { return planName; }
        public String getStatus() { return status; }
    }

    public static class AdminDashboardStatsDto {
        private long totalGymsRegistered;
        private double gymsGrowthPct;
        private long totalActiveUsers;
        private BigDecimal totalRevenue;
        private long newSignupsThisMonthGyms;
        private long newSignupsThisMonthUsers;
        private List<MonthlyRevenueDto> revenueTrend;
        private List<TopGymDto> topGymsByRevenue;

        public AdminDashboardStatsDto(long totalGymsRegistered, double gymsGrowthPct, long totalActiveUsers, BigDecimal totalRevenue, long newSignupsThisMonthGyms, long newSignupsThisMonthUsers, List<MonthlyRevenueDto> revenueTrend, List<TopGymDto> topGymsByRevenue) {
            this.totalGymsRegistered = totalGymsRegistered;
            this.gymsGrowthPct = gymsGrowthPct;
            this.totalActiveUsers = totalActiveUsers;
            this.totalRevenue = totalRevenue;
            this.newSignupsThisMonthGyms = newSignupsThisMonthGyms;
            this.newSignupsThisMonthUsers = newSignupsThisMonthUsers;
            this.revenueTrend = revenueTrend;
            this.topGymsByRevenue = topGymsByRevenue;
        }

        public long getTotalGymsRegistered() { return totalGymsRegistered; }
        public double getGymsGrowthPct() { return gymsGrowthPct; }
        public long getTotalActiveUsers() { return totalActiveUsers; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public long getNewSignupsThisMonthGyms() { return newSignupsThisMonthGyms; }
        public long getNewSignupsThisMonthUsers() { return newSignupsThisMonthUsers; }
        public List<MonthlyRevenueDto> getRevenueTrend() { return revenueTrend; }
        public List<TopGymDto> getTopGymsByRevenue() { return topGymsByRevenue; }
    }

    public static class TopGymDto {
        private UUID gymId;
        private String gymName;
        private String ownerName;
        private BigDecimal totalRevenue;

        public TopGymDto(UUID gymId, String gymName, String ownerName, BigDecimal totalRevenue) {
            this.gymId = gymId;
            this.gymName = gymName;
            this.ownerName = ownerName;
            this.totalRevenue = totalRevenue;
        }

        public UUID getGymId() { return gymId; }
        public String getGymName() { return gymName; }
        public String getOwnerName() { return ownerName; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
    }

    public static class AdminUserDto {
        private Long memberId;
        private String name;
        private String email;
        private String phone;
        private UUID gymId;
        private String gymName;
        private String membershipStatus;
        private LocalDate joinDate;
        private LocalDate lastPaymentDate;

        public AdminUserDto(Long memberId, String name, String email, String phone, UUID gymId, String gymName, String membershipStatus, LocalDate joinDate, LocalDate lastPaymentDate) {
            this.memberId = memberId;
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.gymId = gymId;
            this.gymName = gymName;
            this.membershipStatus = membershipStatus;
            this.joinDate = joinDate;
            this.lastPaymentDate = lastPaymentDate;
        }

        public Long getMemberId() { return memberId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public UUID getGymId() { return gymId; }
        public String getGymName() { return gymName; }
        public String getMembershipStatus() { return membershipStatus; }
        public LocalDate getJoinDate() { return joinDate; }
        public LocalDate getLastPaymentDate() { return lastPaymentDate; }
    }

    public static class PlatformRevenueDto {
        private BigDecimal totalRevenue;
        private List<GymRevenueDto> gymBreakdown;

        public PlatformRevenueDto(BigDecimal totalRevenue, List<GymRevenueDto> gymBreakdown) {
            this.totalRevenue = totalRevenue;
            this.gymBreakdown = gymBreakdown;
        }

        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public List<GymRevenueDto> getGymBreakdown() { return gymBreakdown; }
    }

    public static class GymRevenueDto {
        private UUID gymId;
        private String gymName;
        private String ownerName;
        private BigDecimal revenue;

        public GymRevenueDto(UUID gymId, String gymName, String ownerName, BigDecimal revenue) {
            this.gymId = gymId;
            this.gymName = gymName;
            this.ownerName = ownerName;
            this.revenue = revenue;
        }

        public UUID getGymId() { return gymId; }
        public String getGymName() { return gymName; }
        public String getOwnerName() { return ownerName; }
        public BigDecimal getRevenue() { return revenue; }
    }
}
