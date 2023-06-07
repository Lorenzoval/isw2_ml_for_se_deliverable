package it.lorenzoval.isw2_ml_for_se_deliverable;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JIRAHandler {

    private static final String BUGS_URL = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22{0}%22" +
            "AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR%22status%22=%22resolved%22)AND" +
            "%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt={1}&maxResults={2}";

    private static final String RELEASES_URL = "https://issues.apache.org/jira/rest/api/2/project/{0}";
    private static final Logger logger = Logger.getLogger(JIRAHandler.class.getName());

    private static final DateTimeFormatter fromAPIFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private JIRAHandler() {
    }

    private static List<Release> jsonArrayToList(ReleasesList releasesList, JSONArray jsonArray) {
        List<Release> releases = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            String version = jsonArray.getJSONObject(i).getString("name");
            Release release = releasesList.getReleaseByName(version);
            if (release != null)
                releases.add(release);
        }
        return releases;
    }

    public static List<Release> updateAffectedFiles(List<Release> releases, Issue bug, Pattern p, boolean dropped) {
        List<Release> releaseList = new ArrayList<>();
        for (Release release : releases) {
            for (Commit commit : release.getCommits()) {
                Matcher m = p.matcher(commit.subject());
                if (m.find()) {
                    releaseList.add(release);
                    for (String file : commit.files()) {
                        if (!dropped)
                            release.increaseFixes(file);
                        bug.addAffectedFile(file);
                    }
                }
            }
        }
        return releaseList;
    }

    public static Release getAffectedFilesAndFixedVersion(ReleasesList releasesList, Issue bug) {
        Pattern p = Pattern.compile("\\b" + bug.getKey() + "(?!\\.\\d)\\b", Pattern.CASE_INSENSITIVE);
        List<Release> fixedVersions = new ArrayList<>();
        fixedVersions.addAll(updateAffectedFiles(releasesList.getMain(), bug, p, false));
        fixedVersions.addAll(updateAffectedFiles(releasesList.getDropped(), bug, p, true));
        if (bug.getAffectedFiles().isEmpty()) {
            logger.log(Level.INFO, "Issue {0} is not about java files or has no commit associated, discarded",
                    bug.getKey());
            return null;
        } else {
            return fixedVersions.get(fixedVersions.size() - 1);
        }
    }

    private static int parseVersionsArray(ReleasesList releasesList, List<Issue> bugs, Issue bug,
                                           List<Issue> proportionList, JSONArray jsonArray) {
        List<Release> affectedVersions = jsonArrayToList(releasesList, jsonArray);
        Collections.sort(affectedVersions);
        bug.addAffectedVersions(affectedVersions);
        // Exclude not post-release defect and defects with injected version after fixed version
        if (!bug.getAffectedVersions().isEmpty() && !bug.getInjectedVersion().equals(bug.getFixedVersion())) {
            if (!bug.getInjectedVersion().getJiraReleaseDate().isAfter(bug.getFixedVersion().getJiraReleaseDate())) {
                bugs.add(bug);
                proportionList.add(bug);
                return 0;
            } else {
                return 2;
            }
        }
        return 1;
    }

    private static int getLastIssueId(Issue bug, List<Issue> proportionList, int startIndex) {
        OffsetDateTime currentResolutionDate = bug.getResolutionDate();
        for (int i = startIndex; i < proportionList.size(); i++) {
            if (proportionList.get(i).getResolutionDate().isAfter(currentResolutionDate))
                return i;
        }
        return proportionList.size();
    }

    private static double computeP(List<Issue> proportionList, int movingWindowSize, int lastIssueId) {
        double p = 0;
        int bugs = 0;
        int id = lastIssueId;
        while (bugs < movingWindowSize && id != 0) {
            id--;
            Issue bug = proportionList.get(id);
            int fv = bug.getFixedVersion().getId();
            int iv = bug.getInjectedVersion().getId();
            int ov = bug.getOpeningVersion().getId();
            if (ov == fv)
                continue;
            p += (double) (fv - iv) / (fv - ov);
            bugs++;
        }
        return bugs != 0 ? p / bugs : 1;
    }

    public static void proportion(ReleasesList releasesList, List<Issue> bugs, List<Issue> proportionList,
                                  double movingWindow) {
        bugs.sort(Comparator.comparing(Issue::getResolutionDate));
        proportionList.sort(Comparator.comparing(Issue::getResolutionDate));
        int lastIssueId = 0;
        int movingWindowSize = (int) Math.max(1, Math.round(proportionList.size() * movingWindow));
        for (Issue bug : bugs) {
            if (!bug.getAffectedVersions().isEmpty())
                continue;
            int fv = bug.getFixedVersion().getId();
            int ov = bug.getOpeningVersion().getId();
            double computedIv;
            lastIssueId = getLastIssueId(bug, proportionList, lastIssueId);
            double p = computeP(proportionList, movingWindowSize, lastIssueId);
            logger.log(Level.INFO, "Computed p {0}", p);
            computedIv = fv - (fv - ov) * p;
            bug.addAffectedVersions(releasesList.getReleasesBetween(computedIv, bug.getFixedVersion().getId()));
        }
    }

    public static List<Issue> getBugs(Project project, ReleasesList releasesList) throws IOException,
            URISyntaxException {
        int i = 0;
        int j;
        int total;
        int noCommit = 0;
        int noPostRelease = 0;
        int injectedAfterFixed = 0;
        String urlString;
        List<Issue> bugs = new ArrayList<>();
        List<Issue> proportionList = new ArrayList<>();

        do {
            j = i + 1000;
            urlString = MessageFormat.format(BUGS_URL, project.getProjectName().toUpperCase(Locale.ROOT),
                    Integer.toString(i), Integer.toString(j));
            URI uri = new URI(urlString).parseServerAuthority();

            try (InputStream in = uri.toURL().openStream()) {
                JSONObject json = new JSONObject(IOUtils.toString(in, StandardCharsets.UTF_8));
                JSONArray issues = json.getJSONArray("issues");
                total = json.getInt("total");

                for (; i < total && i < j; i++) {
                    JSONObject jsonObject1 = issues.getJSONObject(i % 1000);
                    JSONObject jsonObject2 = jsonObject1.getJSONObject("fields");
                    String key = jsonObject1.getString("key");
                    JSONArray jsonArray1 = jsonObject2.getJSONArray("versions");
                    Release openingVersion = releasesList.getReleaseByDate(LocalDate
                            .parse(jsonObject2.getString("created"), fromAPIFormatter));
                    OffsetDateTime resolutionDate = OffsetDateTime
                            .parse(jsonObject2.getString("resolutiondate"), fromAPIFormatter);
                    Issue bug = new Issue(key, openingVersion, resolutionDate);
                    Release fixedVersion = getAffectedFilesAndFixedVersion(releasesList, bug);
                    if (fixedVersion == null) {
                        // Do not add issues with no commit associated
                        noCommit++;
                        continue;
                    }
                    bug.setFixedVersion(fixedVersion);
                    if (jsonArray1.length() == 0) {
                        bugs.add(bug);
                    } else {
                        switch (parseVersionsArray(releasesList, bugs, bug, proportionList, jsonArray1)) {
                            case 1 -> noPostRelease++;
                            case 2 -> injectedAfterFixed++;
                            default -> {
                                // Used for case 0
                            }
                        }
                    }
                }
            }

        } while (i < total);

        proportion(releasesList, bugs, proportionList, project.getMovingWindow());

        String logStr = Main.LOG_HEADER + project.getProjectName() + "\nTotal Issues: " + total +
                "\nIssues not discarded: " + bugs.size() + "\nIssues considered for proportion: " +
                proportionList.size() + "\nIssues not about java files or with no commit associated: " + noCommit +
                "\nIssues not post release: " + noPostRelease + "\nIssues with IV > FV: " + injectedAfterFixed;
        logger.log(Level.INFO, logStr);

        return bugs;
    }

    public static List<Release> getReleases(Project project) throws URISyntaxException, IOException,
            InterruptedException {
        String urlString;
        List<Release> releases = new ArrayList<>();
        urlString = MessageFormat.format(RELEASES_URL, project.getProjectName().toUpperCase(Locale.ROOT));
        final String rd = "releaseDate";
        final String n = "name";
        int total;
        URI uri = new URI(urlString).parseServerAuthority();

        try (InputStream in = uri.toURL().openStream()) {
            JSONObject json = new JSONObject(IOUtils.toString(in, StandardCharsets.UTF_8));
            JSONArray versions = json.getJSONArray("versions");
            total = versions.length();

            for (int i = 0; i < total; i++) {
                JSONObject jsonObject = versions.getJSONObject(i);
                if (jsonObject.has(rd)) {
                    if (jsonObject.has(n)) {
                        String releaseName = jsonObject.getString(n);
                        LocalDate gitReleaseDate = GitHandler.getReleaseDate(project, releaseName);
                        // Only add if present in git as well
                        if (gitReleaseDate != null)
                            releases.add(new Release(project, releaseName, gitReleaseDate,
                                    LocalDate.parse(jsonObject.getString(rd))));
                    } else {
                        logger.log(Level.SEVERE, "No name found for release {0}", jsonObject.getString(rd));
                    }
                } else {
                    if (jsonObject.getBoolean("released")) {
                        logger.log(Level.SEVERE, "No release date for release {0} in JSON", i);
                    }
                }
            }
        }

        String logStr = Main.LOG_HEADER + project.getProjectName() + "\nTotal Releases: " + total +
                "\nReleases not discarded: " + releases.size();
        logger.log(Level.INFO, logStr);

        return releases;
    }

}
