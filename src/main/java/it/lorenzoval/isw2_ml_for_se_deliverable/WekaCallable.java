package it.lorenzoval.isw2_ml_for_se_deliverable;

import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.classifiers.CostMatrix;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.evaluation.Evaluation;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.supervised.instance.SpreadSubsample;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WekaCallable implements Callable<WekaResult> {

    private final Logger logger = Logger.getLogger(WekaCallable.class.getName());
    private final WekaResult wekaResult;
    private Instances trainingSet;
    private Instances testingSet;
    private FeatureSelection featureSelection;
    private Balancing balancing;
    private Classifier classifier;
    private weka.classifiers.Classifier actualClassifier;
    private CostSensitivity costSensitivity;

    public WekaCallable(WekaResult wekaResult, Instances trainingSet, Instances testingSet, FeatureSelection featureSelection, Balancing balancing, Classifier classifier, CostSensitivity costSensitivity) {
        this.wekaResult = wekaResult;
        this.trainingSet = trainingSet;
        this.testingSet = testingSet;
        this.featureSelection = featureSelection;
        this.balancing = balancing;
        this.classifier = classifier;
        this.costSensitivity = costSensitivity;
    }

    private int countBuggyInstances(Instances set) {
        int count = 0;
        for (Instance instance : set)
            count += (int) instance.value(instance.numAttributes() - 1) ^ 1;
        return count;
    }

    private double calculatePercentageOversampling() {
        int buggy = countBuggyInstances(this.trainingSet);
        int nonBuggy = this.trainingSet.size() - buggy;

        if (nonBuggy > buggy)
            return 200.0 * nonBuggy / this.trainingSet.size();
        else
            return 200.0 * buggy / this.trainingSet.size();
    }

    private double calculatePercentageSmote() {
        int buggy = countBuggyInstances(this.trainingSet);
        int nonBuggy = this.trainingSet.size() - buggy;

        if (nonBuggy > buggy)
            return buggy != 0 ? 100.0 * (nonBuggy - buggy) / buggy : 0;
        else
            return nonBuggy != 0 ? 100.0 * (buggy - nonBuggy) / nonBuggy : 0;
    }

    private CostMatrix createCostMatrix() {
        CostMatrix costMatrix = new CostMatrix(2);
        costMatrix.setCell(0, 0, 0.0);
        costMatrix.setCell(1, 0, 1.0);
        costMatrix.setCell(0, 1, 10.0);
        costMatrix.setCell(1, 1, 0.0);
        return costMatrix;
    }

    private void evaluateResults() throws Exception {
        final int classIndex = 0;

        this.actualClassifier.buildClassifier(this.trainingSet);

        Evaluation evaluation = new Evaluation(this.testingSet);
        evaluation.evaluateModel(this.actualClassifier, this.testingSet);

        wekaResult.setTP((int) evaluation.numTruePositives(classIndex));
        wekaResult.setFP((int) evaluation.numFalsePositives(classIndex));
        wekaResult.setTN((int) evaluation.numTrueNegatives(classIndex));
        wekaResult.setFN((int) evaluation.numFalseNegatives(classIndex));
        wekaResult.setPrecision(evaluation.precision(classIndex));
        wekaResult.setRecall(evaluation.recall(classIndex));
        wekaResult.setAuc(evaluation.areaUnderROC(classIndex));
        wekaResult.setKappa(evaluation.kappa());
    }

    @Override
    public WekaResult call() throws Exception {
        switch (featureSelection) {
            case NO_FEATURE_SELECTION -> this.wekaResult.setFeatureSelection("No selection");
            case BEST_FIRST -> {
                this.wekaResult.setFeatureSelection("BestFirst");
                AttributeSelection attributeSelection = new AttributeSelection();
                CfsSubsetEval cfsSubsetEval = new CfsSubsetEval();
                BestFirst bestFirst = new BestFirst();
                attributeSelection.setEvaluator(cfsSubsetEval);
                attributeSelection.setSearch(bestFirst);
                attributeSelection.setInputFormat(this.trainingSet);
                this.trainingSet = Filter.useFilter(this.trainingSet, attributeSelection);
                this.testingSet = Filter.useFilter(this.testingSet, attributeSelection);
                StringBuilder stringBuilder = new StringBuilder(Main.LOG_HEADER + wekaResult.getDataset() +
                        "\nAttributes after Feature Selection: ");
                for (int i = 0; i < this.trainingSet.numAttributes(); i++) {
                    stringBuilder.append(this.testingSet.attribute(i).name());
                    stringBuilder.append(" ");
                }
                String logStr = stringBuilder.toString();
                logger.log(Level.INFO, logStr);
            }
            default -> {
                // Mute Sonar
            }
        }
        switch (balancing) {
            case NO_SAMPLING -> this.wekaResult.setBalancing("No sampling");
            case OVERSAMPLING -> {
                this.wekaResult.setBalancing("Oversampling");
                Resample resample = new Resample();
                resample.setInputFormat(this.trainingSet);
                resample.setBiasToUniformClass(1.0);
                resample.setNoReplacement(false);
                resample.setSampleSizePercent(calculatePercentageOversampling());
                this.trainingSet = Filter.useFilter(this.trainingSet, resample);
            }
            case UNDERSAMPLING -> {
                this.wekaResult.setBalancing("Undersampling");
                SpreadSubsample spreadSubsample = new SpreadSubsample();
                spreadSubsample.setDistributionSpread(1.0);
                spreadSubsample.setInputFormat(this.trainingSet);
                this.trainingSet = Filter.useFilter(this.trainingSet, spreadSubsample);
            }
            case SMOTE -> {
                this.wekaResult.setBalancing("SMOTE");
                SMOTE smote = new SMOTE();
                smote.setInputFormat(this.trainingSet);
                smote.setPercentage(calculatePercentageSmote());
                this.trainingSet = Filter.useFilter(this.trainingSet, smote);
            }
        }

        int trainingSetSize = this.trainingSet.size();
        int testingSetSize = this.testingSet.size();
        this.wekaResult.setPercentTrainingReleases(100 * trainingSetSize / (trainingSetSize + testingSetSize));
        int buggy = countBuggyInstances(this.trainingSet);
        int buggyPercent = 100 * buggy / trainingSetSize;
        this.wekaResult.setPercentDefectiveInTraining(buggyPercent);
        buggy = countBuggyInstances(this.testingSet);
        buggyPercent = 100 * buggy / testingSetSize;
        this.wekaResult.setPercentDefectiveInTesting(buggyPercent);

        switch (classifier) {
            case NAIVE_BAYES -> this.actualClassifier = new NaiveBayes();
            case RANDOM_FOREST -> this.actualClassifier = new RandomForest();
            case IBK -> this.actualClassifier = new IBk();
        }
        String name = this.actualClassifier.getClass().getName();
        this.wekaResult.setClassifier(name.substring(name.lastIndexOf('.') + 1));

        CostMatrix costMatrix;
        CostSensitiveClassifier costSensitiveClassifier;

        switch (costSensitivity) {
            case NO_COST_SENSITIVITY -> this.wekaResult.setSensitivity("No cost sensitive");
            case SENSITIVE_THRESHOLD -> {
                this.wekaResult.setSensitivity("Sensitive threshold");
                costMatrix = createCostMatrix();
                costSensitiveClassifier = new CostSensitiveClassifier();
                costSensitiveClassifier.setClassifier(this.actualClassifier);
                costSensitiveClassifier.setCostMatrix(costMatrix);
                costSensitiveClassifier.setMinimizeExpectedCost(true);
                this.actualClassifier = costSensitiveClassifier;
            }
            case SENSITIVE_LEARNING -> {
                this.wekaResult.setSensitivity("Sensitive learning");
                costMatrix = createCostMatrix();
                costSensitiveClassifier = new CostSensitiveClassifier();
                costSensitiveClassifier.setClassifier(this.actualClassifier);
                costSensitiveClassifier.setCostMatrix(costMatrix);
                costSensitiveClassifier.setMinimizeExpectedCost(false);
                this.actualClassifier = costSensitiveClassifier;
            }
        }
        evaluateResults();
        // Prepare for GC
        this.trainingSet = null;
        this.testingSet = null;
        this.featureSelection = null;
        this.balancing = null;
        this.classifier = null;
        this.actualClassifier = null;
        this.costSensitivity = null;
        return this.wekaResult;
    }

}
