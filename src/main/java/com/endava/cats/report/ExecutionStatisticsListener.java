package com.endava.cats.report;

import com.endava.cats.annotations.DryRun;
import lombok.Getter;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Getter
@DryRun
public class ExecutionStatisticsListener {

    private int errors;
    private int warns;
    private int success;
    private int skipped;

    private int authErrors;
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

    public void increaseErrors() {
        this.errors++;
    }

    public void increaseWarns() {
        this.warns++;
    }

    public void increaseSuccess() {
        this.success++;
    }

    public int getAll() {
        return this.success + this.warns + this.errors;
    }

    public boolean areManyAuthErrors() {
        return authErrors > this.getAll() / 2;
    }

    public boolean areManyIoErrors() {
        return ioErrors > this.getAll() / 2;
    }

}
