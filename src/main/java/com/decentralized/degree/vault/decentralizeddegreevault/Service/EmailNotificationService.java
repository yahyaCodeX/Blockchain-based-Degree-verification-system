package com.decentralized.degree.vault.decentralizeddegreevault.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
public class EmailNotificationService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@degreevault.edu.pk}")
    private String senderEmail;

    @Value("${app.verification-base-url:http://localhost:5173}")
    private String baseUrl;

    /**
     * Sends an official MIT-style credential conferral email to the student with their secret Vault Access PIN.
     * If SMTP is not yet configured, gracefully logs the credentials to the console for demonstration.
     */
    public boolean sendStudentVaultCredentials(
            String toEmail,
            String studentName,
            String rollNo,
            String guci,
            String studentPin,
            String universityCode,
            String department
    ) {
        String vaultLink = baseUrl + "/student?id=" + guci + "&pin=" + studentPin;
        String verifyLink = baseUrl + "/verify?id=" + guci;

        log.info("==========================================================================");
        log.info("🎓 [OFFICIAL CONFERRAL EMAIL DISPATCHED] -> {}", toEmail);
        log.info("Student: {} | Roll No: {} | GUCI: {}", studentName, rollNo, guci);
        log.info("🔑 Vault Access PIN: {}", studentPin);
        log.info("🔗 Direct Access Link: {}", vaultLink);
        log.info("==========================================================================");

        if (mailSender == null) {
            log.info("JavaMailSender is not configured in application.properties. Simulated email logged successfully.");
            return true;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, universityCode + " Registrar - Degree Vault");
            helper.setTo(toEmail);
            helper.setSubject("Official Degree Conferred & Anchored on Blockchain: " + studentName + " (" + rollNo + ")");

            String htmlContent = buildHtmlEmail(studentName, rollNo, guci, studentPin, universityCode, department, vaultLink, verifyLink);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Official credential email successfully delivered via SMTP to {}", toEmail);
            return true;

        } catch (Exception e) {
            log.warn("SMTP delivery failed ({}) — Fallback to simulated delivery logged above.", e.getMessage());
            return false;
        }
    }

    private String buildHtmlEmail(
            String studentName,
            String rollNo,
            String guci,
            String pin,
            String universityCode,
            String department,
            String vaultLink,
            String verifyLink
    ) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; background-color: #f4f6fb; margin: 0; padding: 24px; color: #1e293b;'>"
                + "  <div style='max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.05);'>"
                + "    <div style='background: linear-gradient(135deg, #0d9488, #2563eb); padding: 32px 24px; text-align: center; color: #ffffff;'>"
                + "      <h1 style='margin: 0; font-size: 22px; font-weight: 800; letter-spacing: -0.5px;'>" + universityCode + " Degree Vault</h1>"
                + "      <p style='margin: 6px 0 0 0; font-size: 13px; opacity: 0.9;'>Official Blockchain Credential Conferral Notice</p>"
                + "    </div>"
                + "    <div style='padding: 32px 28px;'>"
                + "      <p style='font-size: 15px; margin-top: 0;'>Dear <strong>" + studentName + "</strong>,</p>"
                + "      <p style='font-size: 14px; line-height: 1.6; color: #475569;'>"
                + "        We are pleased to inform you that your official degree of <strong>Bachelor of Engineering in " + department + "</strong> has been officially anchored to the Ethereum EVM blockchain with permanent decentralized IPFS archiving."
                + "      </p>"
                + "      <div style='background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 12px; padding: 20px; margin: 24px 0;'>"
                + "        <div style='font-size: 12px; font-weight: 700; color: #64748b; text-transform: uppercase; margin-bottom: 8px;'>Your Confidential Vault Credentials</div>"
                + "        <div style='font-size: 14px; margin-bottom: 6px;'><strong>Student Roll No:</strong> <span style='font-family: monospace; font-size: 15px; color: #0f172a;'>" + rollNo + "</span></div>"
                + "        <div style='font-size: 14px; margin-bottom: 6px;'><strong>Credential ID (GUCI):</strong> <span style='font-family: monospace; font-size: 14px; color: #0d9488;'>" + guci + "</span></div>"
                + "        <div style='font-size: 14px; margin-bottom: 12px;'><strong>Your Secret Access PIN:</strong> <span style='font-family: monospace; font-size: 20px; font-weight: 900; color: #2563eb; letter-spacing: 2px; background: #e0e7ff; padding: 2px 8px; border-radius: 6px;'>" + pin + "</span></div>"
                + "        <p style='margin: 0; font-size: 12px; color: #64748b;'>Do not share your Secret PIN. Use it to log in to your private Student Vault.</p>"
                + "      </div>"
                + "      <div style='text-align: center; margin: 30px 0;'>"
                + "        <a href='" + vaultLink + "' style='background-color: #0d9488; color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 10px; font-weight: 700; font-size: 14px; display: inline-block;'>Access My Student Vault</a>"
                + "      </div>"
                + "      <p style='font-size: 12px; color: #64748b; line-height: 1.5;'>"
                + "        For third-party employers, visa authorities, and recruiters, you can provide your public verification URL:<br>"
                + "        <a href='" + verifyLink + "' style='color: #2563eb; word-break: break-all;'>" + verifyLink + "</a>"
                + "      </p>"
                + "    </div>"
                + "    <div style='background: #f1f5f9; padding: 16px 24px; text-align: center; font-size: 11px; color: #94a3b8; border-top: 1px solid #e2e8f0;'>"
                + "      Office of the Registrar & Controller of Examinations • Autonomous Public Sector University"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }
}
