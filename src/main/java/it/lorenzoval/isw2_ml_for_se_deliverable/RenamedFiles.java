package it.lorenzoval.isw2_ml_for_se_deliverable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RenamedFiles {

    private final Map<String, Set<String>> files;

    public RenamedFiles() {
        files = new HashMap<>();
    }

    public void add(String oldName, String newName) {
        Set<String> set;
        if (files.containsKey(oldName)) {
            set = files.get(oldName);
            set.add(newName);
        } else {
            set = new HashSet<>();
            set.add(oldName);
            set.add(newName);
            files.put(oldName, set);
        }
        files.put(newName, set);
    }

    public boolean isRenamed(String fileName) {
        return files.containsKey(fileName);
    }

    public Set<String> getNames(String fileName) {
        return files.get(fileName);
    }

    public void clear() {
        files.clear();
    }

}
