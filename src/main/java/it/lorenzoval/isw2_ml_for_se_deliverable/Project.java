package it.lorenzoval.isw2_ml_for_se_deliverable;

public abstract class Project {

    private final String url;
    private final String projectName;
    private final String releaseString;
    private final RenamedFiles renamedFiles;

    protected Project(String url, String projectName, String releaseString) {
        this.url = url;
        this.projectName = projectName;
        this.releaseString = releaseString;
        this.renamedFiles = new RenamedFiles();
    }

    public String getUrl() {
        return this.url;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public String getReleaseString() {
        return this.releaseString;
    }

    public RenamedFiles getRenamedFiles() {
        return this.renamedFiles;
    }

}
