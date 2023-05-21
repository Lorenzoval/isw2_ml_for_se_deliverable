package it.lorenzoval.isw2_ml_for_se_deliverable;

import org.apache.commons.io.FileUtils;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WekaHandler {

    private static final Logger logger = Logger.getLogger(WekaHandler.class.getName());
    private static final String FORMAT = "%n%-15.15s%-15.15s%-15.15s%n";

    private WekaHandler() {
    }

    private static void splitDataset(Instances dataset, Instances trainingSet, Instances testingSet, int i) {
        for (Instance instance : dataset) {
            if (instance.value(0) < i)
                trainingSet.add(instance);
            else if (instance.value(0) == i)
                testingSet.add(instance);
            else
                break;
        }
    }

    private static void iterateAll(Project project, int numTrainingReleases, Instances trainingSet,
                                   Instances testingSet, List<Future<WekaResult>> futures,
                                   ExecutorService executorService) {
        for (FeatureSelection featureSelection : FeatureSelection.values()) {
            for (Balancing balancing : Balancing.values()) {
                for (Classifier classifier : Classifier.values()) {
                    for (CostSensitivity costSensitivity : CostSensitivity.values()) {
                        WekaResult wekaResult = new WekaResult(project.getProjectName(), numTrainingReleases);
                        WekaCallable task = new WekaCallable(wekaResult, new Instances(trainingSet),
                                new Instances(testingSet), featureSelection, balancing, classifier, costSensitivity);
                        futures.add(executorService.submit(task));
                    }
                }
            }
        }
    }

    private static List<String> walkForward(Project project, Instances dataset) throws InterruptedException,
            ExecutionException {
        List<String> lines = new ArrayList<>();
        lines.add(WekaResult.CSV_HEADER);
        // Get number of releases by looking at last instance of dataset
        int numReleases = (int) dataset.lastInstance().value(0);
        logger.log(Level.INFO, "Number of releases in the dataset: {0}", numReleases);
        try (ExecutorService executorService = Executors.newFixedThreadPool(16)) {
            List<Future<WekaResult>> futures = new ArrayList<>();
            // Skip first iteration because it has empty training set
            for (int i = 2; i <= numReleases; i++) {
                Instances trainingSet = new Instances(dataset, 0);
                Instances testingSet = new Instances(dataset, 0);
                splitDataset(dataset, trainingSet, testingSet, i);

                // Delete versions
                trainingSet.deleteAttributeAt(0);
                testingSet.deleteAttributeAt(0);

                String message = String.format(FORMAT, "", "Training set:", "Testing set:")
                        + String.format(FORMAT, "Releases:", i == 2 ? (i - 1) : "[1, " + (i - 1) + "]", i)
                        + String.format(FORMAT, "Size:", trainingSet.size(), testingSet.size());
                logger.log(Level.INFO, message);
                iterateAll(project, i - 1, trainingSet, testingSet, futures, executorService);
            }

            executorService.shutdown();
            for (Future<WekaResult> future : futures) {
                WekaResult wekaResult = future.get();
                lines.add(wekaResult.toCSVLine());
            }
            if (!executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                // Should never happen
                executorService.shutdownNow();
            }
        }

        return lines;
    }

    private static Instances loadCSV(Project project) throws IOException {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(project.getProjectName() + "_metrics.csv"));
        // Set Yes as positive for Buggy
        loader.setNominalLabelSpecs(new Object[]{"Buggy:Yes,No"});
        return loader.getDataSet();
    }

    public static void evaluateDataset(Project project) throws IOException, InterruptedException, ExecutionException {
        File outFile = new File(project.getProjectName() + "_weka.csv");
        Instances dataset = loadCSV(project);
        // Delete class names
        dataset.deleteAttributeAt(1);
        dataset.setClassIndex(dataset.numAttributes() - 1);
        logger.log(Level.INFO, "Applying walk forward for {0}", project.getProjectName());
        FileUtils.writeLines(outFile, walkForward(project, dataset));
    }

}
