package com.endava.cats.report;

import com.endava.cats.annotations.DryRun;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.fusesource.jansi.Ansi;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
@DryRun
public class ExecutionStatisticsListener {

    private final Map<String, Integer> errors = new HashMap<>();
    private final Map<String, Integer> warns = new HashMap<>();
    private final Map<String, Integer> success = new HashMap<>();

    @Getter
    private int skipped;

    @Getter
    private int authErrors;
    @Getter
    private int ioErrors;

    public void increaseAuthErrors() {
        this.authErrors++;
    }

    public void increaseIoErrors() {
        this.ioErrors++;
    }

    public void increaseSkipped() {
        this.skipped++;
    }

    public void increaseErrors(String path) {
        this.errors.merge(path, 1, Integer::sum);
    }

    public void increaseWarns(String path) {
        this.warns.merge(path, 1, Integer::sum);
    }

    public void increaseSuccess(String path) {
        this.success.merge(path, 1, Integer::sum);
    }

    public int getErrors() {
        return this.errors.values().stream().reduce(0, Integer::sum);
    }

    public int getWarns() {
        return this.warns.values().stream().reduce(0, Integer::sum);
    }

    public int getSuccess() {
        return this.success.values().stream().reduce(0, Integer::sum);
    }

    public int getAll() {
        return this.getSuccess() + this.getWarns() + this.getErrors();
    }

    public boolean areManyAuthErrors() {
        return authErrors > this.getAll() / 2;
    }

    public boolean areManyIoErrors() {
        return ioErrors > this.getAll() / 2;
    }

    public String resultAsStringPerPath(String path) {
        String errorsString = Ansi.ansi().fg(Ansi.Color.RED).a("E " + errors.getOrDefault(path, 0)).reset().toString();
        String warnsString = Ansi.ansi().fg(Ansi.Color.YELLOW).a("W " + warns.getOrDefault(path, 0)).reset().toString();
        String successString = Ansi.ansi().fg(Ansi.Color.GREEN).a("S " + success.getOrDefault(path, 0)).reset().toString();
        return "%s, %s, %s".formatted(errorsString, warnsString, successString);
    }

}
