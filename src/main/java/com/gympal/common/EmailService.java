package com.gympal.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "FitTrack - Password Reset Request";
        String body = "Hello,\n\n" +
                "You have requested to reset your password. Click the link below to set a new password:\n\n" +
                resetLink + "\n\n" +
                "This link will expire in 1 hour. If you did not request this, please ignore this email.\n\n" +
                "Thanks,\nFitTrack Team";

        if (javaMailSender != null && !fromEmail.isBlank()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                javaMailSender.send(message);
                logger.info("Password reset email sent to {}", to);
            } catch (Exception e) {
                logger.error("Failed to send password reset email to {}", to, e);
                // Fallback to logging the link
                logger.info("Fallback - Password Reset Link for {}: {}", to, resetLink);
            }
        } else {
            // Mail is not configured, just log the link
            logger.info("Email service not fully configured. Showing reset link in console for: {}", to);
            logger.info("==============================================");
            logger.info("PASSWORD RESET LINK: {}", resetLink);
            logger.info("==============================================");
        }
    }
}
