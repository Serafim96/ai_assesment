package ru.assistant.config;

import org.junit.Test;

import java.util.Optional;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnvSecretResolverTest {

    @Test
    public void resolvesPresentVariableThroughInjectedLookup() {
        Function<String, String> lookup = name -> "SECRET".equals(name) ? "s3cr3t-value" : null;
        EnvSecretResolver resolver = new EnvSecretResolver(lookup);

        Optional<String> result = resolver.resolve("SECRET");

        assertTrue(result.isPresent());
        assertEquals("s3cr3t-value", result.get());
    }

    @Test
    public void missingVariableReturnsEmptyNotException() {
        Function<String, String> lookup = name -> null;
        EnvSecretResolver resolver = new EnvSecretResolver(lookup);

        Optional<String> result = resolver.resolve("MISSING_VAR");

        assertFalse(result.isPresent());
    }

    @Test
    public void defaultConstructorDelegatesToSystemGetenv() {
        EnvSecretResolver resolver = new EnvSecretResolver();

        Optional<String> result = resolver.resolve("SOME_VAR_THAT_ALMOST_CERTAINLY_DOES_NOT_EXIST_12345");

        assertFalse(result.isPresent());
    }
}
