package it.lorenzoval.isw2_ml_for_se_deliverable;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

    public static void buildDataset(Project project) throws IOException, InterruptedException, URISyntaxException {
        File outFile = new File(project.getProjectName() + ".csv");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        lines.add("Version,File Name,LOC");
        List<Release> releases = JIRAHandler.getReleases(project);
        for (Release release : releases) {
            GitHandler.changeRelease(project, release);
            Map<String, Integer> files = TokeiHandler.countLoc(project);
            for (Map.Entry<String, Integer> entry : files.entrySet()) {
                line.setLength(0);
                line.append(release.getName()).append(",").append(entry.getKey()).append(",").append(entry.getValue());
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
