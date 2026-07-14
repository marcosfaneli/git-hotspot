package com.mfaneli;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

record CliArguments(List<File> repositories, Path outputDirectory) {
}
