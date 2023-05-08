package it.lorenzoval.isw2_ml_for_se_deliverable;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TokeiHandler {

    private TokeiHandler() {
    }

    public static Map<String, Integer> countLoc(Project project) throws IOException, InterruptedException {
        String projectName = project.getProjectName();
        File directory = new File(projectName);
        Map<String, Integer> files = new HashMap<>();
        ProcessBuilder pb = new ProcessBuilder("tokei", "-t", "java", "-o", "json");
        pb.directory(directory);
        Process pr = pb.start();
        String output = IOUtils.toString(pr.getInputStream(), StandardCharsets.UTF_8);
        pr.waitFor();
        JSONObject json = new JSONObject(output);
        json = json.getJSONObject("Java");
        JSONArray jsonArray = json.getJSONArray("reports");
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            String fileName = jsonObject.getString("name").substring(2);
            jsonObject = jsonObject.getJSONObject("stats");
            Integer lines = jsonObject.getInt("code");
            files.put(fileName, lines);
        }
        return files;
    }

}
