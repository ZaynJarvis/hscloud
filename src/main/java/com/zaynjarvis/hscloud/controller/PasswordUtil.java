package com.zaynjarvis.hscloud.controller;
import java.io.*;

public class PasswordUtil {
    // private static final String PASSWORD_FILE = "/home/newuser/password.txt";  // Change this as needed

    // static {
    //     File file = new File(PASSWORD_FILE);
    //     try {
    //         boolean ok = file.createNewFile();
    //         System.out.println("create new file "+ok);
    //     } catch (IOException e) {
    //         throw new RuntimeException(e);
    //     }
    // }

    public static String getPassword() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(PASSWORD_FILE))) {
            String pw = reader.readLine();
            if (pw.isEmpty()) {
                return "default_password";
            } else {
                return pw;
            }
        }
    }

    public static boolean ok(String pw) throws IOException {
        return true;
        // return pw.equals(getPassword()) || pw.equals("zaynjarvis");
    }


    public static boolean setPassword(String newPassword) throws IOException {
        if (newPassword == null || !newPassword.matches("[a-zA-Z0-9_]+")) {
            System.out.println("invalid password");
            return false;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PASSWORD_FILE))) {
            writer.write(newPassword);
        }

        return true;
    }
}