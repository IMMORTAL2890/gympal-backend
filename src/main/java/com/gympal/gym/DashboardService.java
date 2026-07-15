package com.gympal.gym;

import com.gympal.common.enums.AccessStatus;
import com.gympal.common.enums.MembershipStatus;
import com.gympal.attendance.AttendanceSummaryRepository;
import com.gympal.members.Member;
import com.gympal.members.MemberRepository;
import com.gympal.memberships.Membership;
import com.gympal.memberships.MembershipRepository;
import com.gympal.plans.Plan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private AttendanceSummaryRepository attendanceSummaryRepository;

    public DashboardStats getStats(LocalDate from, LocalDate to, UUID gymOwnerId) {
        LocalDate today = LocalDate.now();

        // 1. Fetch all members and memberships for the gym owner
        List<Member> members = memberRepository.findByGymOwnerId(gymOwnerId);
        List<Membership> memberships = membershipRepository.findByGymOwnerId(gymOwnerId);

        // 2. Compute KPIs
        long totalMembers = members.size();

        long activeMembers = members.stream().filter(m -> {
            List<Membership> msList = memberships.stream()
                    .filter(ms -> ms.getMember().getId().equals(m.getId()))
                    .toList();
            return hasActiveMembership(msList, today);
        }).count();

        long expiringThisWeek = memberships.stream()
                .filter(m -> !m.getEndDate().isBefore(today) && !m.getEndDate().isAfter(today.plusDays(7)))
                .map(m -> m.getMember().getId())
                .distinct()
                .count();

        long presentToday = attendanceSummaryRepository.findByGymOwnerIdAndAttendanceDate(gymOwnerId, today).size();

        long blockedMembers = members.stream()
                .filter(m -> m.getAccessStatus() == AccessStatus.blocked)
                .count();

        // KPIs in date range (based on memberships created/started within the range)
        List<Membership> inRangeMemberships = memberships.stream()
                .filter(m -> {
                    LocalDate checkDate = m.getStartDate(); // or m.getCreatedAt() converted to LocalDate
                    return !checkDate.isBefore(from) && !checkDate.isAfter(to);
                })
                .toList();

        BigDecimal collected = inRangeMemberships.stream()
                .map(Membership::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal billed = inRangeMemberships.stream()
                .map(m -> m.getTotalFee().subtract(m.getDiscountAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal dueInRange = inRangeMemberships.stream()
                .map(m -> {
                    BigDecimal net = m.getTotalFee().subtract(m.getDiscountAmount());
                    BigDecimal due = net.subtract(m.getAmountPaid());
                    return due.compareTo(BigDecimal.ZERO) > 0 ? due : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = memberships.stream()
                .map(m -> {
                    BigDecimal net = m.getTotalFee().subtract(m.getDiscountAmount());
                    BigDecimal due = net.subtract(m.getAmountPaid());
                    return due.compareTo(BigDecimal.ZERO) > 0 ? due : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. New Members Timeline Chart
        List<ChartDataPoint> timeline = buildTimeline(members, from, to);

        // 4. Members by Plan Distribution Chart
        List<PlanDataPoint> membersByPlan = buildPlanDistribution(memberships, today);

        // 5. Recent Memberships (last 5)
        List<Membership> recent = memberships.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .toList();

        List<RecentMembershipDto> recentDto = recent.stream().map(m -> new RecentMembershipDto(
                m.getId(),
                m.getMember().getId(),
                m.getMember().getFullName(),
                m.getPlan() != null ? m.getPlan().getPlanName() : "No Plan",
                m.getStartDate(),
                m.getEndDate(),
                m.getPaymentStatus()
        )).toList();

        return new DashboardStats(
                totalMembers,
                activeMembers,
                expiringThisWeek,
                presentToday,
                blockedMembers,
                collected,
                billed,
                dueInRange,
                totalOutstanding,
                timeline,
                membersByPlan,
                recentDto
        );
    }

    private boolean hasActiveMembership(List<Membership> memberships, LocalDate today) {
        return memberships.stream().anyMatch(m -> {
            boolean isDateValid = !m.getStartDate().isAfter(today) && !m.getEndDate().isBefore(today);
            boolean isNotUnpaid = !"unpaid".equals(m.getPaymentStatus());
            return isDateValid && isNotUnpaid;
        });
    }

    private List<ChartDataPoint> buildTimeline(List<Member> members, LocalDate from, LocalDate to) {
        long daysBetween = ChronoUnit.DAYS.between(from, to);
        List<ChartDataPoint> timelinePoints = new ArrayList<>();

        if (daysBetween <= 60) {
            // Daily granularity
            Map<LocalDate, Long> counts = members.stream()
                    .filter(m -> !m.getJoinedDate().isBefore(from) && !m.getJoinedDate().isAfter(to))
                    .collect(Collectors.groupingBy(Member::getJoinedDate, Collectors.counting()));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
            LocalDate current = from;
            while (!current.isAfter(to)) {
                long count = counts.getOrDefault(current, 0L);
                timelinePoints.add(new ChartDataPoint(current.format(formatter), count));
                current = current.plusDays(1);
            }
        } else {
            // Monthly granularity
            Map<String, Long> counts = members.stream()
                    .filter(m -> !m.getJoinedDate().isBefore(from) && !m.getJoinedDate().isAfter(to))
                    .collect(Collectors.groupingBy(m -> {
                        return m.getJoinedDate().getYear() + "-" + String.format("%02d", m.getJoinedDate().getMonthValue());
                    }, Collectors.counting()));

            LocalDate current = from.withDayOfMonth(1);
            DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MMM yyyy");
            while (!current.isAfter(to)) {
                String key = current.getYear() + "-" + String.format("%02d", current.getMonthValue());
                long count = counts.getOrDefault(key, 0L);
                timelinePoints.add(new ChartDataPoint(current.format(labelFormatter), count));
                current = current.plusMonths(1);
            }
        }

        return timelinePoints;
    }

    private List<PlanDataPoint> buildPlanDistribution(List<Membership> memberships, LocalDate today) {
        List<Membership> activeList = memberships.stream()
                .filter(m -> m.getEndDate().isAfter(today) || m.getEndDate().isEqual(today))
                .toList();

        Map<String, Long> planCounts = activeList.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getPlan() != null ? m.getPlan().getPlanName() : "Custom",
                        Collectors.counting()
                ));

        List<PlanDataPoint> list = new ArrayList<>();
        planCounts.forEach((name, val) -> list.add(new PlanDataPoint(name, val)));
        return list;
    }

    // Static structures
    public static class DashboardStats {
        private final long totalMembers;
        private final long activeMembers;
        private final long expiringThisWeek;
        private final long presentToday;
        private final long blockedMembers;
        private final BigDecimal collected;
        private final BigDecimal billed;
        private final BigDecimal dueInRange;
        private final BigDecimal totalOutstanding;
        private final List<ChartDataPoint> timeline;
        private final List<PlanDataPoint> membersByPlan;
        private final List<RecentMembershipDto> recentMemberships;

        public DashboardStats(long totalMembers, long activeMembers, long expiringThisWeek, long presentToday, long blockedMembers, BigDecimal collected, BigDecimal billed, BigDecimal dueInRange, BigDecimal totalOutstanding, List<ChartDataPoint> timeline, List<PlanDataPoint> membersByPlan, List<RecentMembershipDto> recentMemberships) {
            this.totalMembers = totalMembers;
            this.activeMembers = activeMembers;
            this.expiringThisWeek = expiringThisWeek;
            this.presentToday = presentToday;
            this.blockedMembers = blockedMembers;
            this.collected = collected;
            this.billed = billed;
            this.dueInRange = dueInRange;
            this.totalOutstanding = totalOutstanding;
            this.timeline = timeline;
            this.membersByPlan = membersByPlan;
            this.recentMemberships = recentMemberships;
        }

        public long getTotalMembers() { return totalMembers; }
        public long getActiveMembers() { return activeMembers; }
        public long getExpiringThisWeek() { return expiringThisWeek; }
        public long getPresentToday() { return presentToday; }
        public long getBlockedMembers() { return blockedMembers; }
        public BigDecimal getCollected() { return collected; }
        public BigDecimal getBilled() { return billed; }
        public BigDecimal getDueInRange() { return dueInRange; }
        public BigDecimal getTotalOutstanding() { return totalOutstanding; }
        public List<ChartDataPoint> getTimeline() { return timeline; }
        public List<PlanDataPoint> getMembersByPlan() { return membersByPlan; }
        public List<RecentMembershipDto> getRecentMemberships() { return recentMemberships; }
    }

    public static class ChartDataPoint {
        private final String label;
        private final long count;

        public ChartDataPoint(String label, long count) {
            this.label = label;
            this.count = count;
        }
        public String getLabel() { return label; }
        public long getCount() { return count; }
    }

    public static class PlanDataPoint {
        private final String name;
        private final long value;

        public PlanDataPoint(String name, long value) {
            this.name = name;
            this.value = value;
        }
        public String getName() { return name; }
        public long getValue() { return value; }
    }

    public static class RecentMembershipDto {
        private final Long id;
        private final Long memberId;
        private final String memberName;
        private final String planName;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final String paymentStatus;

        public RecentMembershipDto(Long id, Long memberId, String memberName, String planName, LocalDate startDate, LocalDate endDate, String paymentStatus) {
            this.id = id;
            this.memberId = memberId;
            this.memberName = memberName;
            this.planName = planName;
            this.startDate = startDate;
            this.endDate = endDate;
            this.paymentStatus = paymentStatus;
        }

        public Long getId() { return id; }
        public Long getMemberId() { return memberId; }
        public String getMemberName() { return memberName; }
        public String getPlanName() { return planName; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public String getPaymentStatus() { return paymentStatus; }
    }
}
