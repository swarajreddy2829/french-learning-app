package com.example.frenchlearning.security;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.Objects;

final class JwtSigningKeys {

    private final RSAKey rsaKey;

    JwtSigningKeys(RSAKey rsaKey) {
        this.rsaKey = Objects.requireNonNull(rsaKey, "rsaKey must not be null");
    }

    RSAKey rsaKey() {
        return rsaKey;
    }

    @Override
    public String toString() {
        return JwtKeyLoader.describe(rsaKey);
    }
}
