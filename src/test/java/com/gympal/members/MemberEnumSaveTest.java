package com.gympal.members;

import com.gympal.admin.AdminSeeder;
import com.gympal.common.enums.AccessStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MemberEnumSaveTest {

    @Autowired
    private MemberRepository memberRepository;

    @MockBean
    private AdminSeeder adminSeeder;

    @Test
    public void testSaveMemberWithAllAccessStatusValues() {
        UUID gymOwnerId = UUID.randomUUID();
        
        for (AccessStatus status : AccessStatus.values()) {
            Member member = Member.builder()
                    .fullName("Test Member " + status)
                    .mobileNumber("9988776655")
                    .joinedDate(LocalDate.now())
                    .gymOwnerId(gymOwnerId)
                    .accessStatus(status)
                    .build();

            Member saved = memberRepository.save(member);
            Assertions.assertNotNull(saved);
            Assertions.assertNotNull(saved.getId());
            Assertions.assertEquals(status, saved.getAccessStatus());
            
            // Retrieve and confirm it loads correctly
            Member retrieved = memberRepository.findById(saved.getId()).orElse(null);
            Assertions.assertNotNull(retrieved);
            Assertions.assertEquals(status, retrieved.getAccessStatus());
        }
    }
}
