package com.mfaneli;

import java.util.Map;

public record GitHotspotResult(int commitCount, Map<String, Integer> fileChanges) {

    public GitHotspotResult {
        fileChanges = Map.copyOf(fileChanges);
    }
}
