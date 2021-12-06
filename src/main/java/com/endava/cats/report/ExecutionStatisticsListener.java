package com.endava.cats.report;

import com.endava.cats.aop.DryRun;
import lombok.Getter;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Getter
@DryRun
public class ExecutionStatisticsListener {

    private int errors;
    private int warns;
    private int success;
    private int skipped;

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

}
