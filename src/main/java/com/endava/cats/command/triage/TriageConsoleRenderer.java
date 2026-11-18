package com.endava.cats.command.triage;

import com.endava.cats.util.AnsiUtils;
import com.google.common.base.Splitter;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.IntStream;

/** Human-readable triage output; full reproducer data remains available independently of console verbosity. */
public final class TriageConsoleRenderer {
    private TriageConsoleRenderer() {
    }

    public static void render(PrettyLogger logger, TriageReport report, boolean showReproducers) {
        new Context(logger, report, showReproducers).render();
    }

    private record Context(PrettyLogger logger, TriageReport report, boolean showReproducers) {
        private static final String SEPARATOR = "-".repeat(80);
        private static final String BANNER = "=".repeat(80);

        void render() {
            renderSummary();
            List<TriageProblem> problems = report.problems();
            IntStream.range(0, problems.size()).forEach(index -> renderProblem(index + 1, problems.get(index)));
            renderCorrelations();
            if (!showReproducers && !problems.isEmpty()) {
                logger.noFormat("  Reproducers: add --show-reproducers or --export-reproducers <file>.");
            }
            logger.noFormat(AnsiUtils.bold(BANNER));
        }

        private void renderSummary() {
            logger.noFormat(AnsiUtils.bold(BANNER));
            logger.noFormat(AnsiUtils.bold("  CATS Report Triage: Problems and Root-Cause Diagnosis"));
            logger.noFormat(AnsiUtils.bold(BANNER));
            logger.noFormat("  Report Folder : " + report.reportFolder());
            logger.noFormat("  Saved Tests   : %d  (Errors: %d, Warnings: %d)"
                    .formatted(report.totalTestsScanned(), report.totalErrors(), report.totalWarnings()));
            logger.noFormat("  Problems      : %d on %d operation occurrences  (Grouping Reduction: %s%%)"
                    .formatted(report.totalProblems(), report.totalOccurrences(), report.noiseReductionPercent()));
            logger.noFormat(SEPARATOR);
        }

        private void renderProblem(int rank, TriageProblem problem) {
            logger.noFormat("  %2d. %s %s  %s".formatted(rank, levelBadge(problem), AnsiUtils.bold(problem.title()), "(id " + problem.problemId() + ")"));
            logger.noFormat("      %s · %s · %s".formatted(String.join(" · ", problem.reasons()), count(problem.testCount(), "test"), count(problem.occurrences().size(), "operation")));
            problem.occurrences().forEach(this::renderOccurrence);
            logger.noFormat("");
        }

        private void renderOccurrence(TriageOccurrence occurrence) {
            logger.noFormat("      - %s %s -> HTTP %s (%s)%s".formatted(categoryBadge(occurrence), AnsiUtils.bold(occurrence.operation()),
                    occurrence.statusCodes(), count(occurrence.testCount(), "test"), verificationBadge(occurrence)));
            logger.noFormat("        Diagnosis   : " + occurrence.diagnosis());
            if (StringUtils.isNotBlank(occurrence.attribution())) {
                logger.noFormat("        Attribution : " + occurrence.attribution());
            }
            logger.noFormat("        Remediation : " + occurrence.remediation());
            renderDetails(occurrence);
        }

        private void renderDetails(TriageOccurrence occurrence) {
            if (!occurrence.fields().isEmpty()) {
                logger.noFormat("        Fields      : " + String.join(", ", occurrence.fields()));
            }
            logger.noFormat("        Fuzzers     : " + String.join(", ", occurrence.fuzzers()));
            logger.noFormat("        Sample Tests: " + String.join(", ", occurrence.sampleTestIds()));
            if (showReproducers) {
                logger.noFormat("        Reproducer  : " + occurrence.curlReproducer());
            }
            if (StringUtils.isNotBlank(occurrence.verificationNote())) {
                logger.noFormat("        Verification: " + occurrence.verificationNote());
            }
        }

        private static String levelBadge(TriageProblem problem) {
            return "error".equals(problem.level()) ? AnsiUtils.red("[ERROR]") : AnsiUtils.yellow("[WARN]");
        }

        private static String categoryBadge(TriageOccurrence occurrence) {
            String badge = "[" + occurrence.category() + "]";
            return switch (occurrence.severityRank()) {
                case 0 -> AnsiUtils.red(badge);
                case 1 -> AnsiUtils.yellow(badge);
                default -> AnsiUtils.blue(badge);
            };
        }

        private static String verificationBadge(TriageOccurrence occurrence) {
            if (occurrence.verificationStatus() == null) {
                return "";
            }
            String text = " [%s -> HTTP %s]".formatted(occurrence.verificationStatus().replace('_', ' '),
                    occurrence.verifiedStatusCode() == null ? "n/a" : occurrence.verifiedStatusCode());
            return switch (occurrence.verificationStatus()) {
                case "FIXED" -> AnsiUtils.green(text);
                case "STILL_FAILING" -> AnsiUtils.red(text);
                default -> AnsiUtils.yellow(text);
            };
        }

        private void renderCorrelations() {
            if (report.synthesizedCorrelations().isEmpty()) {
                return;
            }
            logger.noFormat(AnsiUtils.bold("  Synthesized --correlations YAML Rules (pass --export-correlations <file>):"));
            logger.noFormat(SEPARATOR);
            Splitter.on('\n').split(report.synthesizedCorrelationsYaml()).forEach(line -> logger.noFormat("  " + line));
        }

        private static String count(int value, String noun) {
            return "%d %s%s".formatted(value, noun, value == 1 ? "" : "s");
        }
    }
}
