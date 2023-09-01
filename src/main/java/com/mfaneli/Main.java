package com.mfaneli;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final String PATH = "C:\\Users\\marco\\projects\\usadosbr\\oferta.montadora.nissan";
    private static final Map<String, Integer> files = new HashMap<>();

    public static void main(String[] args) {

        try {
            List<String> commitIds = obterCommitIds(PATH);

            for (String commitId : commitIds) {
                obterArquivosAlteradosNoCommit(commitId);
            }

            System.out.println("Quantidade de commits: " + commitIds.size());

            for (Map.Entry<String, Integer> entry : files.entrySet()) {
                System.out.println(entry.getKey() + " - " + entry.getValue());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<String> obterCommitIds(String path) throws IOException {
        BufferedReader bufferedReader = executeCommand("git log");

        List<String> commitIds = new ArrayList<>();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            if (line.startsWith("commit")) {
                commitIds.add(line.substring(7));
            }
        }

        return commitIds;
    }

    private static void obterArquivosAlteradosNoCommit(String commitId) throws IOException {
        BufferedReader bufferedReader = executeCommand("git diff-tree --no-commit-id --name-only -r " + commitId);

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            if (files.containsKey(line)) {
                files.put(line, files.get(line) + 1);
            } else {
                files.put(line, 1);
            }
        }
    }

    private static ProcessBuilder obterProcessBuilder() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(new File(PATH));

        return processBuilder;
    }

    private static String[] obterTerminalCommand() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return new String[]{"cmd.exe", "/c"};
        }
        return new String[]{"bash", "-c"};
    }

    private static BufferedReader executeCommand(String comando) throws IOException {
        String[] terminalCommand = obterTerminalCommand();
        ProcessBuilder processBuilder = obterProcessBuilder();
        processBuilder.command(terminalCommand[0], terminalCommand[1], comando);

        Process process = processBuilder.start();

        InputStream inputStream = process.getInputStream();
        InputStreamReader reader = new InputStreamReader(inputStream);
        return new BufferedReader(reader);
    }
}
