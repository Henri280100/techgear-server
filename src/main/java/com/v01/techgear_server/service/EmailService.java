package com.v01.techgear_server.service;


import com.v01.techgear_server.model.User;

public interface EmailService {
    void sendVerificationEmail(User user);

    String generateEmailBody(String verificationUrl, String username);
    
    void sendResetTokenEmail(String email, String resetToken);

    String verifyEmail(String urlToken);

    void resendVerificationEmail(String email);

}
