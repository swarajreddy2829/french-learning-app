package com.example.frenchlearning.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

final class JwtKeyLoader {

    private static final String PKCS8_PRIVATE_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS8_PRIVATE_FOOTER = "-----END PRIVATE KEY-----";
    private static final String X509_PUBLIC_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String X509_PUBLIC_FOOTER = "-----END PUBLIC KEY-----";

    private JwtKeyLoader() {}

    static RSAKey load(String privateKeyLocation, String publicKeyLocation, ResourceLoader resourceLoader) {
        Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        RSAPrivateKey privateKey = readPrivateKey(resolve(privateKeyLocation, resourceLoader));
        RSAPublicKey publicKey = readPublicKey(resolve(publicKeyLocation, resourceLoader));
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("JWT public key does not match the configured private key");
        }
        try {
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException exception) {
            throw new IllegalStateException("JWT key material is malformed or incompatible", exception);
        }
    }

    private static Resource resolve(String location, ResourceLoader resourceLoader) {
        if (!StringUtils.hasText(location)) {
            throw new IllegalStateException("JWT key location is not configured");
        }
        String trimmed = location.trim();
        Resource resource = resourceLoader.getResource(trimmed);
        if (resource.exists()) {
            return resource;
        }
        try {
            Resource fileResource = new FileSystemResource(Path.of(trimmed));
            if (fileResource.exists()) {
                return fileResource;
            }
        } catch (InvalidPathException ignored) {
            // file: URIs are resolved by ResourceLoader; a missing resource falls through.
        }
        throw new IllegalStateException("JWT key material was not found at the configured location");
    }

    private static RSAPrivateKey readPrivateKey(Resource resource) {
        String pem = readPem(resource);
        if (!pem.contains(PKCS8_PRIVATE_HEADER) || pem.contains("ENCRYPTED")) {
            throw new IllegalStateException("JWT private key is malformed or incompatible");
        }
        try {
            PrivateKey privateKey =
                    KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decodePem(pem)));
            if (privateKey instanceof RSAPrivateKey rsaPrivateKey) {
                return rsaPrivateKey;
            }
            throw new IllegalStateException("JWT private key is malformed or incompatible");
        } catch (InvalidKeySpecException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JWT private key is malformed or incompatible", exception);
        }
    }

    private static RSAPublicKey readPublicKey(Resource resource) {
        String pem = readPem(resource);
        if (!pem.contains(X509_PUBLIC_HEADER)) {
            throw new IllegalStateException("JWT public key is malformed or incompatible");
        }
        try {
            PublicKey publicKey =
                    KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodePem(pem)));
            if (publicKey instanceof RSAPublicKey rsaPublicKey) {
                return rsaPublicKey;
            }
            throw new IllegalStateException("JWT public key is malformed or incompatible");
        } catch (InvalidKeySpecException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JWT public key is malformed or incompatible", exception);
        }
    }

    private static String readPem(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.US_ASCII);
        } catch (IOException exception) {
            throw new IllegalStateException("JWT key material could not be read", exception);
        }
    }

    private static byte[] decodePem(String pem) {
        String body = pem.replace(PKCS8_PRIVATE_HEADER, "")
                .replace(PKCS8_PRIVATE_FOOTER, "")
                .replace(X509_PUBLIC_HEADER, "")
                .replace(X509_PUBLIC_FOOTER, "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "");
        String base64 = body.replaceAll("\\s+", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT key material is malformed or incompatible", exception);
        }
    }

    static String describe(RSAKey rsaKey) {
        return "JwtSigningKey[kid=%s, alg=%s]"
                .formatted(
                        rsaKey.getKeyID(),
                        rsaKey.getAlgorithm() == null
                                ? JWSAlgorithm.RS256.getName()
                                : rsaKey.getAlgorithm().getName().toUpperCase(Locale.ROOT));
    }
}
