package it.lorenzoval.isw2_ml_for_se_deliverable;

import java.time.LocalDate;
import java.util.*;

public class Release implements Comparable<Release> {

    private final Project project;
    private final String name;
    private final LocalDate gitReleaseDate; // Used for file age
    private final LocalDate jiraReleaseDate; // Used for operations related to bugs
    private final Map<String, Metrics> files;
    private final List<Commit> commits;
    int id;

    public Release(Project project, String name, LocalDate gitReleaseDate, LocalDate jiraReleaseDate) {
        this.project = project;
        this.name = name;
        this.gitReleaseDate = gitReleaseDate;
        this.jiraReleaseDate = jiraReleaseDate;
        this.files = new HashMap<>();
        this.commits = new ArrayList<>();
    }

    public String getName() {
        return this.name;
    }

    public LocalDate getGitReleaseDate() {
        return this.gitReleaseDate;
    }

    public LocalDate getJiraReleaseDate() {
        return this.jiraReleaseDate;
    }

    public Map<String, Metrics> getFiles() {
        return this.files;
    }

    public List<Commit> getCommits() {
        return this.commits;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void addFile(String fileName, long loc, LocalDate creationDate) {
        files.put(fileName, new Metrics(loc, creationDate, gitReleaseDate));
    }

    public void addFile(String fileName) {
        // Dummy addFile for dropped releases
        files.put(fileName, new Metrics());
    }

    public void addCommit(Commit commit) {
        this.commits.add(commit);
    }

    public void increaseFixes(String fileName) {
        if (!files.containsKey(fileName)) {
            RenamedFiles renamedFiles = this.project.getRenamedFiles();
            if (renamedFiles.isRenamed(fileName)) {
                for (String alias : renamedFiles.getNames(fileName)) {
                    if (files.containsKey(alias)) {
                        files.get(alias).increaseFixes();
                    }
                }
            }
        } else {
            files.get(fileName).increaseFixes();
        }
    }

    public void updateMetrics(String fileName, String author, int chgSetSize, int locAdded, int locDeleted) {
        if (!files.containsKey(fileName)) {
            RenamedFiles renamedFiles = this.project.getRenamedFiles();
            if (renamedFiles.isRenamed(fileName)) {
                for (String alias : renamedFiles.getNames(fileName)) {
                    if (files.containsKey(alias)) {
                        files.get(alias).updateFromCommit(author, chgSetSize, locAdded, locDeleted);
                    }
                }
            }
        } else {
            files.get(fileName).updateFromCommit(author, chgSetSize, locAdded, locDeleted);
        }
    }

    public void setBuggy(String fileName) {
        if (!files.containsKey(fileName)) {
            RenamedFiles renamedFiles = this.project.getRenamedFiles();
            if (renamedFiles.isRenamed(fileName)) {
                for (String alias : renamedFiles.getNames(fileName)) {
                    if (files.containsKey(alias)) {
                        files.get(alias).setBuggy();
                    }
                }
            }
        } else {
            files.get(fileName).setBuggy();
        }
    }

    @Override
    public int compareTo(Release release) {
        return this.gitReleaseDate.compareTo(release.getGitReleaseDate());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || this.getClass() != o.getClass())
            return false;
        Release release = (Release) o;
        return this.name.equals(release.name) && this.gitReleaseDate.equals(release.gitReleaseDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.gitReleaseDate);
    }

}
