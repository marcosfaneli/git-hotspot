package com.mfaneli;

import java.io.File;

public record RepositoryHotspotResult(File repository, GitHotspotResult hotspot) {
}
