package com.sibim.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.util.Scanner;

/**
 * Utilidad de línea de comandos para generar contraseñas hasheadas con BCrypt.
 * Uso (desde el proyecto): java -cp target/sibim-desktop-*.jar com.sibim.util.HashUtil
 * O directamente a través de packaging/crear-admin.ps1
 */
public class HashUtil {
    public static void main(String[] args) {
        String password;
        if (args.length > 0) {
            password = args[0];
        } else {
            System.out.print("Contraseña a hashear: ");
            password = new Scanner(System.in).nextLine().trim();
        }
        if (password.isBlank()) {
            System.err.println("ERROR: la contraseña no puede estar vacía");
            System.exit(1);
        }
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        System.out.println(hash);
    }
}
