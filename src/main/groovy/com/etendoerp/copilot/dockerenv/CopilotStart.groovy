package com.etendoerp.copilot.dockerenv

import com.etendoerp.copilot.Constants
import org.gradle.api.Project
import org.gradle.process.ExecResult

import java.io.ByteArrayOutputStream

class CopilotStart {

    static void load(Project project) {

        project.tasks.register("copilot.start") {
            dependsOn({ project.tasks.named("copilotEnvironmentVerification") })
            doLast {
                project.logger.info("*****************************************************")
                project.logger.info("* Performing copilot start task.")
                project.logger.info("*****************************************************")

                String copilotPort = project.ext.get(Constants.COPILOT_PORT_PROPERTY)
                String copilotTag = getCopilotImageTag(project)
                boolean copilotPullImage = getPullDockerImage(project)
                String copilotDockerContainerName = getContainerName(project)

                if (copilotPullImage) {
                    project.logger.info("Pulling Docker image...")
                    pullDockerImage(project, copilotTag)

                }
                project.logger.info("Deleting existing container...")
                delete_container(project, copilotDockerContainerName)
                project.logger.info("Creating new container...")
                createNewContainer(copilotDockerContainerName, copilotPort, project, copilotTag)

            }
        }
    }

    static String getCopilotImageTag(Project project) {
        String defaultTag = "master"
        try {
            String copilotTag = project.ext.get(Constants.COPILOT_IMAGE_TAG)
            if (copilotTag.isEmpty()) {
                return defaultTag
            }
            return copilotTag
        }catch (Exception e) {
            project.logger.info("Failed to get COPILOT_IMAGE_TAG: " + e.getMessage() + ". Default value \"master\" will be used.")
        }
        return defaultTag
    }

    private static String getContainerName(Project project) {
        def defaultName = "copilot-default"
        try {
            String copilotDockerContainerName = project.ext.get(Constants.COPILOT_DOCKER_CONTAINER_NAME)
            if (copilotDockerContainerName.isEmpty()) {
                return defaultName
            }
            return copilotDockerContainerName
        } catch (Exception e) {
            project.logger.info("Failed to get COPILOT_DOCKER_CONTAINER_NAME: " + e.getMessage() + ". Default value \"copilot-default\" will be used.")
        }
        return defaultName
    }

    private static boolean getPullDockerImage(Project project) {
        boolean defaultResult = true;
        try {
            String copilotPullImage = project.ext.get(Constants.COPILOT_PULL_IMAGE)
            if (copilotPullImage.isEmpty()) {
                    return defaultResult
            } else {
                return copilotPullImage.toLowerCase().equals("true")
            }
        } catch (Exception e) {
            project.logger.info("Failed to get COPILOT_PULL_IMAGE: " + e.getMessage() + ". Default value \"true\" will be used.")
        }
        return defaultResult
    }

    private static boolean pullDockerImage(Project project, String copilotTag) {
        ByteArrayOutputStream stdOut = new ByteArrayOutputStream()
        def execResult = project.exec {
            commandLine 'sh', '-c', 'docker pull etendo/' + "${Constants.COPILOT_DOCKER_REPO}:${copilotTag}"
            standardOutput = stdOut
        }
        def stdOutString = stdOut.toString()
        if (execResult.exitValue == 0 && !stdOutString.trim().contains("Image is up to date")) {
            project.logger.info("Docker image updated successfully..")
            return true
        }
        return false
    }

    private static void createNewContainer(String copilotDockerContainerName, String copilotPort, Project project, String copilotTag) {
        String dockerRunCommand = "docker run --env-file=\$(pwd)/gradle.properties --name ${copilotDockerContainerName}  -p " + "${copilotPort}" + ':' + "${copilotPort}" +
                ' -v ' + "${project.buildDir.path}/copilot/:/app/ " +
                '-v ' + "\$(pwd)/modules:/modules/ etendo/${Constants.COPILOT_DOCKER_REPO}:${copilotTag}"
        project.exec {
            commandLine 'sh', '-c', dockerRunCommand
        }
    }

    private static void delete_container(Project project, String copilotDockerContainerName) {
        try {
            project.exec {
                commandLine 'sh', '-c', 'docker rm ' + copilotDockerContainerName
            }
        } catch (Exception e) {
            project.logger.warn("Failed to remove Docker container: " + e.getMessage())
        }
    }
}
