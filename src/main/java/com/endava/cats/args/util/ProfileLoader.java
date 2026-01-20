package com.endava.cats.args.util;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
public class ProfileLoader {
    private final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(ProfileLoader.class);
    private static final String BUILT_IN_PROFILES = "fuzzer-profiles.yml";

    private Map<String, Profile> profiles;

    public ProfileLoader() {
        loadBuiltInProfiles();
    }

    @SuppressWarnings("unchecked")
    private void loadBuiltInProfiles() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(BUILT_IN_PROFILES)) {
            if (is == null) {
                LOGGER.error("Built-in profiles file not found: {}", BUILT_IN_PROFILES);
                profiles = new HashMap<>();
                return;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            Map<String, Object> profilesData = (Map<String, Object>) data.get("profiles");

            profiles = new HashMap<>();
            for (Map.Entry<String, Object> entry : profilesData.entrySet()) {
                String profileName = entry.getKey();
                Map<String, Object> profileData = (Map<String, Object>) entry.getValue();

                Profile profile = new Profile(
                        profileName,
                        (String) profileData.get("description"),
                        (List<String>) profileData.getOrDefault("fuzzers", Collections.emptyList())
                );
                profiles.put(profileName, profile);
            }

            LOGGER.debug("Loaded {} built-in profiles", profiles.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load built-in profiles", e);
            profiles = new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    public void loadCustomProfiles(Path customProfileFile) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(Files.newInputStream(customProfileFile));
            Map<String, Object> profilesData = (Map<String, Object>) data.get("profiles");

            for (Map.Entry<String, Object> entry : profilesData.entrySet()) {
                String profileName = entry.getKey();
                Map<String, Object> profileData = (Map<String, Object>) entry.getValue();

                Profile profile = new Profile(
                        profileName,
                        (String) profileData.get("description"),
                        (List<String>) profileData.getOrDefault("fuzzers", Collections.emptyList())
                );

                if (profiles.containsKey(profileName)) {
                    LOGGER.info("Overriding built-in profile: {}", profileName);
                }
                profiles.put(profileName, profile);
            }

            LOGGER.debug("Loaded custom profiles from: {}", customProfileFile);
        } catch (Exception e) {
            LOGGER.error("Failed to load custom profiles from: {}", customProfileFile, e);
        }
    }

    public Optional<Profile> getProfile(String profileName) {
        return Optional.ofNullable(profiles.get(profileName));
    }

    public Set<String> getAvailableProfiles() {
        return profiles.keySet();
    }

    public void listProfiles() {
        LOGGER.info("Available profiles:");
        profiles.values().stream()
                .sorted(Comparator.comparing(Profile::name))
                .forEach(profile -> LOGGER.noFormat("  {} - {} ({} fuzzers)",
                        profile.name(),
                        profile.description(),
                        profile.fuzzers().isEmpty() ? "ALL" : profile.fuzzers().size()));
    }

    public record Profile(String name, String description, List<String> fuzzers) {
    }
}