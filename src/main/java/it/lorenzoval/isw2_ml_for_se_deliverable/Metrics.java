package it.lorenzoval.isw2_ml_for_se_deliverable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;

public class Metrics {

    private final long loc;
    private final HashSet<String> authors;
    private final long age;
    private int locTouched;
    private int numRevs;
    private int numFixes;
    private int locAdded;
    private int maxLocAdded;
    private double avgLocAdded;
    private int churn;
    private int maxChurn;
    private double avgChurn;
    private int chgSetSize;
    private int maxChgSetSize;
    private double avgChgSetSize;
    private boolean buggy;

    public Metrics() {
        // Dummy constructor for dropped releases
        this.loc = 0;
        this.authors = null;
        this.age = 0;
    }

    public Metrics(long loc, LocalDate creationDate, LocalDate releaseDate) {
        this.loc = loc;
        this.locTouched = 0;
        this.numRevs = 0;
        this.numFixes = 0;
        this.authors = new HashSet<>();
        this.locAdded = 0;
        this.maxLocAdded = 0;
        this.avgLocAdded = 0;
        this.churn = 0;
        this.maxChurn = 0;
        this.avgChurn = 0;
        this.chgSetSize = 0;
        this.maxChgSetSize = 0;
        this.avgChgSetSize = 0;
        this.age = ChronoUnit.WEEKS.between(creationDate, releaseDate);
        this.buggy = false;
    }

    public long getLoc() {
        return this.loc;
    }

    public int getLocTouched() {
        return this.locTouched;
    }

    public int getNumRevs() {
        return this.numRevs;
    }

    public int getNumFixes() {
        return this.numFixes;
    }

    public int getNumAuthors() {
        return this.authors.size();
    }

    public int getLocAdded() {
        return this.locAdded;
    }

    public int getMaxLocAdded() {
        return this.maxLocAdded;
    }

    public double getAvgLocAdded() {
        return this.avgLocAdded;
    }

    public int getChurn() {
        return this.churn;
    }

    public int getMaxChurn() {
        return this.maxChurn;
    }

    public double getAvgChurn() {
        return this.avgChurn;
    }

    public int getChgSetSize() {
        return this.chgSetSize;
    }

    public int getMaxChgSetSize() {
        return this.maxChgSetSize;
    }

    public double getAvgChgSetSize() {
        return this.avgChgSetSize;
    }

    public long getAge() {
        return this.age;
    }

    public long getWeightedAge() {
        return this.age * this.locTouched;
    }

    public boolean isBuggy() {
        return this.buggy;
    }

    public void setBuggy() {
        this.buggy = true;
    }

    public void increaseFixes() {
        this.numFixes++;
    }

    public void updateFromCommit(String author, int chgSetSize, int locAdded, int locDeleted) {
        this.locTouched += locAdded + locDeleted;
        this.numRevs++;
        this.authors.add(author);
        this.locAdded += locAdded;
        this.maxLocAdded = Math.max(this.maxLocAdded, locAdded);
        this.avgLocAdded += (locAdded - this.avgLocAdded) / numRevs;
        int tempChurn = locAdded - locDeleted;
        this.churn += tempChurn;
        this.maxChurn = Math.max(this.maxChurn, tempChurn);
        this.avgChurn += (tempChurn - this.avgChurn) / numRevs;
        this.chgSetSize += chgSetSize;
        this.maxChgSetSize = Math.max(this.maxChgSetSize, chgSetSize);
        this.avgChgSetSize += (chgSetSize - this.avgChgSetSize) / numRevs;
    }

}
