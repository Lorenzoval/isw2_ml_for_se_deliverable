package it.lorenzoval.isw2_ml_for_se_deliverable;


import java.time.LocalDate;

public class Release {

    private final String name;
    private final LocalDate releaseDate;

    public Release(String name, LocalDate releaseDate) {
        this.name = name;
        this.releaseDate = releaseDate;
    }

    public String getName() {
        return this.name;
    }

    public LocalDate getReleaseDate() {
        return this.releaseDate;
    }

}
