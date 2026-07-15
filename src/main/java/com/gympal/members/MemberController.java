package com.gympal.members;

import com.gympal.common.GymOwnerContext;
import com.gympal.memberships.Membership;
import com.gympal.memberships.MembershipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private GymOwnerContext gymOwnerContext;

    @GetMapping
    public ResponseEntity<List<MemberService.MemberResponseDto>> getMembers(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "dues", required = false) String dues) {
        return ResponseEntity.ok(memberService.getMembers(query, status, dues, gymOwnerContext.getGymOwnerId()));
    }

    @PostMapping
    public ResponseEntity<Member> createMember(@RequestBody MemberService.MemberCreateDto dto) {
        return ResponseEntity.ok(memberService.createMember(dto, gymOwnerContext.getGymOwnerId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MemberService.MemberDetailDto> getMember(@PathVariable Long id) {
        return ResponseEntity.ok(memberService.getMemberDetail(id, gymOwnerContext.getGymOwnerId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Member> updateMember(@PathVariable Long id, @RequestBody MemberService.MemberUpdateDto dto) {
        return ResponseEntity.ok(memberService.updateMember(id, dto, gymOwnerContext.getGymOwnerId()));
    }

    @PostMapping("/{id}/access")
    public ResponseEntity<Member> updateAccess(@PathVariable Long id, @RequestBody MemberService.AccessUpdateDto dto) {
        return ResponseEntity.ok(memberService.updateAccess(id, dto, gymOwnerContext.getGymOwnerId()));
    }

    @PostMapping("/{id}/memberships")
    public ResponseEntity<Membership> assignMembership(@PathVariable Long id, @RequestBody MembershipService.AssignDto dto) {
        return ResponseEntity.ok(membershipService.assignMembership(id, dto, gymOwnerContext.getGymOwnerId()));
    }
}
