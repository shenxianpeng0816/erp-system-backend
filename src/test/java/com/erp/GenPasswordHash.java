package com.erp;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenPasswordHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String[] passwords = {"Admin@123", "admin123", "123456"};
        for (String pw : passwords) {
            System.out.println(pw + "  =>  " + encoder.encode(pw));
        }
    }
}
