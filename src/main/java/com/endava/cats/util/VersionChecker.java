package com.endava.cats.util;

import com.endava.cats.json.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

/**
 * Checks if there is a new CATS version available
 */
@Singleton
public class VersionChecker {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(VersionChecker.class);
    protected static String baseUrl = "https://api.github.com/repos/Endava/cats/releases/latest";
    private static final String DOWNLOAD_URL = "https://github.com/Endava/cats/releases/tag/";
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build();

    /**
     * Checks if a new version is available compared to the current version.
     *
     * @param currentVersion the current semantic version
     * @return a CheckResult with information about the new version, if present
     */
    public CheckResult checkForNewVersion(String currentVersion) {
        boolean updateAvailable = false;
        String downloadLink = null;
        String latestVersion = null;
        String releaseNotes = null;
        try (Response response = httpClient.newCall(new Request.Builder().url(baseUrl).build()).execute()) {
            if (response.body() != null) {
                String responseBody = response.body().string();
                latestVersion = String.valueOf(JsonUtils.getVariableFromJson(responseBody, "$.tag_name"));

                downloadLink = DOWNLOAD_URL + latestVersion;
                latestVersion = latestVersion.replace("cats-", "");
                updateAvailable = compare(currentVersion, latestVersion) < 0;

                releaseNotes = String.valueOf(JsonUtils.getVariableFromJson(responseBody, "$.body"));
            }
        } catch (Exception e) {
            LOGGER.debug("Exception while checking latest version", e);
        }
        return CheckResult.builder()
                .newVersion(updateAvailable)
                .downloadUrl(downloadLink)
                .version(latestVersion)
                .releaseNotes(releaseNotes)
                .build();
    }

    static int compare(String version1, String version2) {
        String[] parts1 = version1.replace("-SNAPSHOT", "").split("\\.", -1);
        String[] parts2 = version2.replace("-SNAPSHOT", "").split("\\.", -1);

        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int number1 = (i < parts1.length) ? Integer.parseInt(parts1[i]) : 0;
            int number2 = (i < parts2.length) ? Integer.parseInt(parts2[i]) : 0;

            if (number1 < number2) {
                return -1;
            } else if (number1 > number2) {
                return 1;
            }
        }

        return 0;
    }

    /**
     * Represents the result of a version check operation.
     */
    @Builder
    @Getter
    @ToString
    public static class CheckResult {
        /**
         * Indicates whether a new version is available.
         */
        private boolean newVersion;

        /**
         * The version information.
         */
        private String version;

        /**
         * The download URL for the new version.
         */
        private String downloadUrl;

        /**
         * The release notes for the new version.
         */
        private String releaseNotes;
    }
}
