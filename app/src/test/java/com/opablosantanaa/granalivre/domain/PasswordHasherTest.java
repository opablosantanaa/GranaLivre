package com.opablosantanaa.granalivre.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordHasherTest {
    @Test
    public void correctPasswordIsAccepted() {
        String hash = PasswordHasher.hash("senhaSegura123");
        assertTrue(PasswordHasher.verify("senhaSegura123", hash));
    }

    @Test
    public void wrongPasswordIsRejected() {
        String hash = PasswordHasher.hash("senhaSegura123");
        assertFalse(PasswordHasher.verify("senhaErrada123", hash));
    }

    @Test
    public void samePasswordProducesDifferentHashes() {
        assertNotEquals(PasswordHasher.hash("senhaSegura123"), PasswordHasher.hash("senhaSegura123"));
    }

    @Test
    public void malformedHashIsRejected() {
        assertFalse(PasswordHasher.verify("senhaSegura123", "invalido"));
        assertFalse(PasswordHasher.verify("senhaSegura123", "abc:%%%:%%%"));
    }
}
