package com.ailearning.platform.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    private final TokenHasher tokenHasher = new TokenHasher();

    @Test
    void returnsStableLowercaseSha256Hex() {
        assertThat(tokenHasher.sha256("refresh-token"))
                .isEqualTo("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120");
    }
}
