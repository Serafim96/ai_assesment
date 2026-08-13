package ru.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class ConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    public static AppConfig load(Path path) {
        AppConfig config;
        try {
            config = MAPPER.readValue(path.toFile(), AppConfig.class);
        } catch (IOException e) {
            throw new ConfigException("Failed to read config file: " + path.getFileName(), e);
        }
        validate(config, path);
        return config;
    }

    private static void validate(AppConfig config, Path path) {
        if (config == null) {
            throw new ConfigException("Config file is empty: " + path.getFileName());
        }
        requireNotNull(config.getLlm(), "llm", path);
        requireNotNull(config.getAgent(), "agent", path);
        requireNotNull(config.getStore(), "store", path);
        requireNotNull(config.getMail(), "mail", path);
        requireNotBlank(config.getLlm().getEndpoint(), "llm.endpoint", path);
        requireNotBlank(config.getLlm().getModel(), "llm.model", path);
        requireNotBlank(config.getLlm().getApiKeyEnv(), "llm.apiKeyEnv", path);
        requireNotBlank(config.getStore().getPath(), "store.path", path);
        requireNotBlank(config.getMail().getProfile(), "mail.profile", path);
        requireNotBlank(config.getMail().getFolder(), "mail.folder", path);
    }

    private static void requireNotNull(Object value, String field, Path path) {
        if (value == null) {
            throw new ConfigException("Missing required config section: " + field + " in " + path.getFileName());
        }
    }

    private static void requireNotBlank(String value, String field, Path path) {
        if (value == null || value.trim().isEmpty()) {
            throw new ConfigException("Missing required config field: " + field + " in " + path.getFileName());
        }
    }

    private ConfigLoader() {
    }
}
