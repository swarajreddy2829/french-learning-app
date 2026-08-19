package com.example.frenchlearning.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

final class JwtTestKeySupport {

    private JwtTestKeySupport() {}

    static KeyFiles writePkcs8Pair(Path directoryPath) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            Path directory = Files.createDirectories(directoryPath);
            Path privateKeyPath = directory.resolve("private.pem");
            Path publicKeyPath = directory.resolve("public.pem");
            Files.writeString(privateKeyPath, pem("PRIVATE KEY", privateKey.getEncoded()), StandardCharsets.US_ASCII);
            Files.writeString(publicKeyPath, pem("PUBLIC KEY", publicKey.getEncoded()), StandardCharsets.US_ASCII);
            return new KeyFiles(privateKeyPath, publicKeyPath, privateKey, publicKey);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate JWT test keys", exception);
        }
    }

    static String pem(String type, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    record KeyFiles(
            Path privateKeyPath, Path publicKeyPath, RSAPrivateKey privateKey, RSAPublicKey publicKey) {}
}
