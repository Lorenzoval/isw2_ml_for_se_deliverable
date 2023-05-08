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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JIRAHandler {

    private static final String BUGS_URL = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22{0}%22" +
            "AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR%22status%22=%22resolved%22)AND" +
            "%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt={1}&maxResults={2}";

    private static final String RELEASES_URL = "https://issues.apache.org/jira/rest/api/2/project/{0}";
    private static final Logger logger = Logger.getLogger(JIRAHandler.class.getName());

    private JIRAHandler() {
    }

    public static List<Issue> getBugs(Project project) throws URISyntaxException {
        int i = 0;
        int j;
        int total = 1;
        String urlString;
        List<Issue> bugs = new ArrayList<>();

        do {
            j = i + 1000;
            urlString = MessageFormat.format(BUGS_URL, project.getProjectName().toUpperCase(Locale.ROOT), Integer.toString(i), Integer.toString(j));
            URI uri = new URI(urlString).parseServerAuthority();

            try (InputStream in = uri.toURL().openStream()) {
                JSONObject json = new JSONObject(IOUtils.toString(in, StandardCharsets.UTF_8));
                JSONArray issues = json.getJSONArray("issues");
                total = json.getInt("total");

                for (; i < total && i < j; i++) {
                    bugs.add(new Issue(issues.getJSONObject(i % 1000).get("key").toString()));
                }

            } catch (IOException ioe) {
                logger.log(Level.WARNING, ioe.toString());
            }

        } while (i < total);

        return bugs;
    }

    public static List<Release> getReleases(Project project) throws URISyntaxException {
        String urlString;
        List<Release> releases = new ArrayList<>();
        urlString = MessageFormat.format(RELEASES_URL, project.getProjectName().toUpperCase(Locale.ROOT));
        final String rd = "releaseDate";
        final String n = "name";
        URI uri = new URI(urlString).parseServerAuthority();

        try (InputStream in = uri.toURL().openStream()) {
            JSONObject json = new JSONObject(IOUtils.toString(in, StandardCharsets.UTF_8));
            JSONArray versions = json.getJSONArray("versions");

            for (int i = 0; i < versions.length(); i++) {
                JSONObject jsonObject = versions.getJSONObject(i);
                if (jsonObject.has(rd)) {
                    if (jsonObject.has(n)) {
                        releases.add(new Release(jsonObject.getString(n), LocalDate.parse(jsonObject.getString(rd))));
                    } else {
                        logger.log(Level.SEVERE, "No name found for release {0}", jsonObject.getString(rd));
                    }
                } else {
                    if (jsonObject.getBoolean("released")) {
                        logger.log(Level.SEVERE, "No release date for release {0} in JSON", i);
                    }
                }
            }

        } catch (IOException ioe) {
            logger.log(Level.WARNING, ioe.toString());
        }

        return releases;
    }

}
