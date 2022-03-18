package com.etendoerp.publication.git

import org.gradle.api.Project

import java.nio.file.Files;
import java.nio.file.Path;

// copy from https://gist.github.com/Crydust/fd1b94afc52cd0f7dd4c
class Git {

    static void gitInit(Project project, Path directory) throws IOException, InterruptedException {
        runCommand(project, directory, "git", "init");
    }

    static void gitStage(Project project, Path directory) throws IOException, InterruptedException {
        runCommand(project, directory, "git", "add", "-A");
    }

    static void gitCommit(Project project, Path directory, String message) throws IOException, InterruptedException {
        runCommand(project, directory, "git", "commit", "-m", message);
    }

    static void gitPush(Project project, Path directory) throws IOException, InterruptedException {
        runCommand(project, directory, "git", "push");
    }

    static void gitClone(Project project, Path directory, String originUrl) throws IOException, InterruptedException {
        runCommand(project, directory.getParent(), "git", "clone", originUrl, directory.getFileName().toString());
    }

    static void gitTag(Project project, Path directory, String tagName, String tagMessage) {
        runCommand(project, directory, "git", "tag", "-a", tagName, "-m", tagMessage)
    }

    static void gitPushTag(Project project, Path directory) {
        runCommand(project, directory, "git", "push", "--tags")
    }

    static void runCommand(Project project, Path directory, String... command) throws IOException, InterruptedException {
        Objects.requireNonNull(directory, "directory");
        if (!Files.exists(directory)) {
            throw new RuntimeException("can't run command in non-existing directory '" + directory + "'");
        }
        ProcessBuilder pb = new ProcessBuilder()
                .command(command)
                .directory(directory.toFile());
        Process p = pb.start();
        StreamGobbler errorGobbler = new StreamGobbler(project, p.getErrorStream(), "");
        StreamGobbler outputGobbler = new StreamGobbler(project, p.getInputStream(), "");
        outputGobbler.start();
        errorGobbler.start();
        int exit = p.waitFor();
        errorGobbler.join();
        outputGobbler.join();
        if (exit != 0) {
            throw new IllegalAccessError(String.format("runCommand '${command}' returned %d", exit));
        }
    }

    private static class StreamGobbler extends Thread {

        private final InputStream is
        private final String type
        private final Project project

        private StreamGobbler(Project project, InputStream is, String type) {
            this.is = is
            this.type = type
            this.project = project
        }

        @Override
        void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(is))
                String line;
                while ((line = br.readLine()) != null) {
                    project.logger.info(type + "> " + line);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

}
