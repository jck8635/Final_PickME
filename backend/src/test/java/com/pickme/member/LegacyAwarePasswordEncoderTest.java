package com.pickme.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 기존 Flask(Werkzeug 3) 가 만든 해시를 그대로 검증할 수 있는지 확인. */
public class LegacyAwarePasswordEncoderTest {

    // werkzeug.security.generate_password_hash('pickme-test-pw', ...) 로 생성
    public static final String SCRYPT = "scrypt:32768:8:1$HBljIoNtnOICdvdv$17ede0e227e5f308fd2630b488722e5f690906b073024319a6d9813cb19cefb881b4e5a0e2bcac18ee70c30eddc3f3691517fbdf4c477ddd2d7e0bf1b03475f7";
    static final String PBKDF2 = "pbkdf2:sha256:1000$1z5p2vyMtU45MNSu$e80d671e4601d4494f381813969a264effe1771504924935ebaae3c2cd5a2254";

    final LegacyAwarePasswordEncoder encoder = new LegacyAwarePasswordEncoder();

    @Test
    void verifiesWerkzeugScrypt() {
        assertThat(encoder.matches("pickme-test-pw", SCRYPT)).isTrue();
        assertThat(encoder.matches("wrong-password", SCRYPT)).isFalse();
    }

    @Test
    void verifiesWerkzeugPbkdf2() {
        assertThat(encoder.matches("pickme-test-pw", PBKDF2)).isTrue();
        assertThat(encoder.matches("wrong-password", PBKDF2)).isFalse();
    }

    @Test
    void newPasswordsUseBcrypt() {
        String hash = encoder.encode("new-password-1");
        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches("new-password-1", hash)).isTrue();
    }

    @Test
    void rejectsGarbage() {
        assertThat(encoder.matches("x", "scrypt:broken")).isFalse();
        assertThat(encoder.matches("x", "not-a-hash")).isFalse();
    }
}
