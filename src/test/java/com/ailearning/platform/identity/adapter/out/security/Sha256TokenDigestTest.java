package com.ailearning.platform.identity.adapter.out.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256TokenDigestTest {

    private final Sha256TokenDigest tokenHasher = new Sha256TokenDigest();

    @Test
    void returnsStableLowercaseSha256Hex() {
        assertThat(tokenHasher.digest("refresh-token"))
                .isEqualTo("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120");
    }
}
