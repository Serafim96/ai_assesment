package ru.assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;

public class EnvSecretResolver {

    private static final Logger log = LoggerFactory.getLogger(EnvSecretResolver.class);

    private final Function<String, String> lookup;

    public EnvSecretResolver() {
        this(System::getenv);
    }

    public EnvSecretResolver(Function<String, String> lookup) {
        this.lookup = lookup;
    }

    public Optional<String> resolve(String envVarName) {
        String value = lookup.apply(envVarName);
        if (value == null) {
            log.warn("Environment variable {} is not set", envVarName);
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
