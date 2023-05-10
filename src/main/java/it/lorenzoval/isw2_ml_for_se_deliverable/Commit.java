package it.lorenzoval.isw2_ml_for_se_deliverable;

import java.util.List;

public record Commit(String hash, String author, String subject, List<String> files) {

}
