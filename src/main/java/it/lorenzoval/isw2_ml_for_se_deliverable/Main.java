package it.lorenzoval.isw2_ml_for_se_deliverable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
        Project syncope = new Syncope();
        Project bookkeeper = new Bookkeeper();
        GitHandler.cloneOrPull(syncope);
        GitHandler.cloneOrPull(bookkeeper);

        List<Release> syncopeReleases = JIRAHandler.getReleases(syncope);
        int i = 0;

        for (Release release : syncopeReleases) {
            i++;
            logger.log(Level.INFO, "{0} {1}", new Object[]{release.getName(), release.getReleaseDate()});
        }
        logger.log(Level.INFO, "Total releases: {0}", i);

        List<Release> bookkeeperReleases = JIRAHandler.getReleases(bookkeeper);
        i = 0;

        for (Release release : bookkeeperReleases) {
            i++;
            logger.log(Level.INFO, "{0} {1}", new Object[]{release.getName(), release.getReleaseDate()});
        }
        logger.log(Level.INFO, "Total releases: {0}", i);

    }

}
