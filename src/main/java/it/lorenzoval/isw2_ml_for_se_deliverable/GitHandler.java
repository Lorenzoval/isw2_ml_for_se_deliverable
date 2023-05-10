package it.lorenzoval.isw2_ml_for_se_deliverable;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GitHandler {

    private static final Logger logger = Logger.getLogger(GitHandler.class.getName());
    private static final String GIT = "/usr/bin/git";
    private static final String NP = "--no-pager";
    private static final String DATE_FORMAT = "--format=%cs";

    private GitHandler() {
    }

    public static void cloneOrPull(Project project) throws IOException, InterruptedException {
        String projectName = project.getProjectName();
        String url = project.getUrl();
        ProcessBuilder pb;
        File file = new File(projectName);
        String logMsg;
        if (file.exists()) {
            if (!file.isDirectory()) {
                String errorMsg = MessageFormat.format("File {0} exists in process path and is not a directory",
                        projectName);
                logger.log(Level.SEVERE, errorMsg);
                throw new IOException();
            } else {
                logMsg = MessageFormat.format("Updating {0} source code", projectName);
                pb = new ProcessBuilder(GIT, "fetch", "--all");
                pb.directory(file);
            }
        } else {
            logMsg = MessageFormat.format("Downloading {0} source code", projectName);
            pb = new ProcessBuilder(GIT, "clone", url);
        }
        logger.log(Level.INFO, logMsg);
        pb.inheritIO();
        Process pr = pb.start();
        pr.waitFor();
    }

    public static LocalDate getReleaseDate(Project project, String releaseName) throws IOException,
            InterruptedException {
        String projectName = project.getProjectName();
        File file = new File(projectName);
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(file);
        pb.command(GIT, "log", "-1", DATE_FORMAT, MessageFormat.format(project.getReleaseString(), releaseName));
        Process pr = pb.start();
        String output = IOUtils.toString(pr.getInputStream(), StandardCharsets.UTF_8);
        pr.waitFor();
        if (pr.exitValue() != 0) {
            return null;
        } else {
            // Remove \n
            output = output.substring(0, output.length() - 1);
            return LocalDate.parse(output);
        }
    }

    public static void changeRelease(Project project, Release release) throws IOException, InterruptedException {
        String projectName = project.getProjectName();
        String tagName = MessageFormat.format(project.getReleaseString(), release.getName());
        File file = new File(projectName);
        ProcessBuilder pb = new ProcessBuilder(GIT, "checkout", MessageFormat.format("tags/{0}",
                tagName));
        pb.directory(file);
        pb.inheritIO();
        Process pr = pb.start();
        pr.waitFor();
    }

    public static LocalDate getFileCreationDate(Project project, String fileName) throws IOException, InterruptedException {
        String projectName = project.getProjectName();
        File file = new File(projectName);
        ProcessBuilder pb = new ProcessBuilder(GIT, "log", "-1", "--diff-filter=A", "--follow",
                DATE_FORMAT, "--", fileName);
        pb.directory(file);
        Process pr = pb.start();
        String output = IOUtils.toString(pr.getInputStream(), StandardCharsets.UTF_8);
        pr.waitFor();
        if (output.isEmpty()) {
            // If it was not possible to find commit in which file got added, take first commit of file
            // --follow is broken if used with --reverse
            pb.command(GIT, NP, "log", "--reverse", DATE_FORMAT, fileName);
            pr = pb.start();
            output = IOUtils.toString(pr.getInputStream(), StandardCharsets.UTF_8);
            pr.waitFor();
            output = output.substring(0, output.indexOf("\n"));
        } else {
            // Remove \n
            output = output.substring(0, output.length() - 1);
        }
        return LocalDate.parse(output);
    }

    private static int countFiles(String[] lines, int i) {
        int j = i;
        while (j < lines.length - 1) {
            if (lines[j + 1].isEmpty() || lines[j + 1].charAt(0) == '$')
                break;
            j++;
        }
        return j - i;
    }

    public static AbstractMap.SimpleEntry<String, String> getNames(String rename) {
        int start;
        int end;
        if (rename.contains("{")) {
            start = rename.indexOf("{");
            end = rename.indexOf("}");
            // Remove curly brackets
            rename = rename.substring(0, start) + rename.substring(start + 1, end) +
                    rename.substring(end + 1);
            end--;
        } else {
            start = 0;
            end = rename.length();
        }
        int pivot = rename.indexOf(" => ");
        String common = rename.substring(0, start);
        // Remove / if already there
        int oldEnd = end;
        int newEnd = end;
        if (rename.charAt(pivot - 1) == '/')
            oldEnd++;
        if (rename.charAt(pivot + 4) == '/')
            newEnd++;
        String oldName = common + rename.substring(start, pivot) + rename.substring(oldEnd);
        String newName = common + rename.substring(pivot + 4, end) + rename.substring(newEnd);
        return new AbstractMap.SimpleEntry<>(oldName, newName);
    }

    public static void parseNumstat(Project project, String line, String author, int chgSetSize, Release release,
                                    List<String> files) {
        int locAdded;
        int locDeleted;
        String[] temp = line.split("\t");
        String fileName = temp[2];
        String oldName = null;
        if (fileName.contains(" => ")) {
            AbstractMap.SimpleEntry<String, String> names = getNames(fileName);
            fileName = names.getValue();
            oldName = names.getKey();
        }
        // Compute metrics for java files
        if (fileName.endsWith(".java")) {
            if (oldName != null)
                project.getRenamedFiles().add(oldName, fileName);
            locAdded = Integer.parseInt(temp[0]);
            locDeleted = Integer.parseInt(temp[1]);
            files.add(fileName);
            release.updateMetrics(fileName, author, chgSetSize, locAdded, locDeleted);
        }
    }

    public static void addCommitIfNotEmpty(Release release, Commit commit) {
        // Only consider commits related to at least one java file
        if (!commit.files().isEmpty())
            release.addCommit(commit);
    }

    public static void parseLines(Project project, String output, Release release) {
        String[] lines = output.split("\n");
        String hash = null;
        String author = null;
        String subject = null;
        int chgSetSize = 0;
        List<String> files = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String str = lines[i];
            if (!str.isEmpty()) {
                if (str.charAt(0) == '$') {
                    // Add previous commit
                    if (hash != null) {
                        addCommitIfNotEmpty(release, new Commit(hash, author, subject, files));
                        files = new ArrayList<>();
                    }

                    chgSetSize = countFiles(lines, i) - 1; // Files committed together with C
                    String[] values = str.substring(1).split("\\$");
                    hash = values[0];
                    author = values[1];
                    subject = values[2];
                } else {
                    parseNumstat(project, str, author, chgSetSize, release, files);
                }
            }
        }
        // Add last commit
        addCommitIfNotEmpty(release, new Commit(hash, author, subject, files));
    }

    public static void getReleaseCommitRelatedMetrics(Project project, Release... releases)
            throws IOException, InterruptedException {
        String projectName = project.getProjectName();
        File file = new File(projectName);
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(file);
        if (releases.length == 1) {
            // Get first commit
            pb.command(GIT, NP, "log", "--reverse");
            Process pr = pb.start();
            String output = IOUtils.toString(pr.getInputStream(), StandardCharsets.UTF_8);
            pr.waitFor();
            String firstCommit = output.substring("commit ".length(), output.indexOf("\n"));
            pb.command(GIT, NP, "log", "--boundary", "--numstat", "--pretty=format:$%h$%an$%s",
                    firstCommit + ".." + MessageFormat.format(project.getReleaseString(), releases[0].getName()));
        } else if (releases.length == 2) {
            pb.command(GIT, NP, "log", "--numstat", "--pretty=format:$%h$%an$%s",
                    MessageFormat.format(project.getReleaseString(), releases[0].getName()) + ".." +
                            MessageFormat.format(project.getReleaseString(), releases[1].getName()));
        }
        Process pr = pb.start();
        String output = IOUtils.toString(pr.getInputStream(), StandardCharsets.UTF_8);
        pr.waitFor();
        if (releases.length == 1) {
            parseLines(project, output, releases[0]);
        } else if (releases.length == 2) {
            parseLines(project, output, releases[1]);
        }
    }

    public static void getCommitRelatedMetrics(Project project, List<Release> releases)
            throws IOException, InterruptedException {
        // Get commits for first release
        getReleaseCommitRelatedMetrics(project, releases.get(0));
        for (int i = 1; i < releases.size(); i++) {
            getReleaseCommitRelatedMetrics(project, releases.get(i - 1), releases.get(i));
        }
    }

}
