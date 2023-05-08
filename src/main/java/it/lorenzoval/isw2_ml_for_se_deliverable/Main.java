package it.lorenzoval.isw2_ml_for_se_deliverable;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    public static void buildDataset(Project project) throws IOException, InterruptedException, URISyntaxException {
        File outFile = new File(project.getProjectName() + ".csv");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        lines.add("Version,File Name");
        List<Release> releases = JIRAHandler.getReleases(project);
        for (Release release : releases) {
            GitHandler.changeRelease(project, release);
            List<String> files = GitHandler.getFiles(project);
            for (String file : files) {
                line.setLength(0);
                line.append(release.getName()).append(",").append(file);
                lines.add(line.toString());
            }
        }
        FileUtils.writeLines(outFile, lines);
    }

    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
        Project syncope = new Syncope();
        Project bookkeeper = new Bookkeeper();
        GitHandler.cloneOrPull(syncope);
        GitHandler.cloneOrPull(bookkeeper);
        buildDataset(syncope);
        buildDataset(bookkeeper);
    }

}
