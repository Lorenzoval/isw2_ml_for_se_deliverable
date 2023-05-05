package it.lorenzoval.isw2_ml_for_se_deliverable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
        Project syncope = new Syncope();
        String syncopeProjectName = syncope.getProjectName();
        String syncopeUrl = syncope.getUrl();
        ProcessBuilder pb;
        File file = new File(syncopeProjectName);
        String logMsg;
        if (file.exists()) {
            if (!file.isDirectory()) {
                String errorMsg = MessageFormat.format("File {0} exists in process path and is not a directory", syncopeProjectName);
                logger.log(Level.SEVERE, errorMsg);
                return;
            } else {
                logMsg = MessageFormat.format("Updating {0} source code", syncopeProjectName);
                pb = new ProcessBuilder("git", "pull");
                pb.directory(file);
            }
        } else {
            logMsg = MessageFormat.format("Downloading {0} source code", syncopeProjectName);
            pb = new ProcessBuilder("git", "clone", syncopeUrl);
        }
        logger.log(Level.INFO, logMsg);
        pb.inheritIO();
        Process pr = pb.start();
        pr.waitFor();

        List<Issue> bugs = JIRAHandler.getBugs(syncope);
        for (Issue bug : bugs) {
            logger.log(Level.INFO, bug.getKey());
        }
    }

}
