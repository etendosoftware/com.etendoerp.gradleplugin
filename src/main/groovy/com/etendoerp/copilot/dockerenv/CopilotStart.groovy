package com.etendoerp.copilot.dockerenv

import com.etendoerp.copilot.Constants
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecException

class CopilotStart {

    public static final String TRUE = 'true'
    public static final String SH = 'sh'
    public static final String DASH_C = '-c'

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
                    project.logger.info('Pulling Docker image...')
                    pullDockerImage(project, copilotTag)
                }
                project.logger.info('Deleting existing container...')
                delete_container(project, copilotDockerContainerName)
                project.logger.info('Creating new container...')
                runNewContainer(copilotDockerContainerName, copilotPort, project, copilotTag)
            }
        }
    }

    static String getCopilotImageTag(Project project) {
        String defaultTag = 'master'
        try {
            String copilotTag = project.ext.get(Constants.COPILOT_IMAGE_TAG)
            if (copilotTag.isEmpty()) {
                return defaultTag
            }
            return copilotTag
        } catch (ExtraPropertiesExtension.UnknownPropertyException e) {
            project.logger.info("Failed to get COPILOT_IMAGE_TAG: ${e.getMessage()}." +
                    " Default value 'master' will be used.")
        }
        return defaultTag
    }

    private static String getContainerName(Project project) {
        String defaultName = 'copilot-default'
        try {
            String copilotDockerContainerName = project.ext.get(Constants.COPILOT_DOCKER_CONTAINER_NAME)
            if (copilotDockerContainerName.isEmpty()) {
                return defaultName
            }
            return copilotDockerContainerName
        } catch (ExtraPropertiesExtension.UnknownPropertyException e) {
            project.logger.info("Failed to get COPILOT_DOCKER_CONTAINER_NAME: ${e.getMessage()}." +
                    " Default value 'copilot-default' will be used.")
        }
        return defaultName
    }

    private static boolean getPullDockerImage(Project project) {
        boolean defaultResult = true
        try {
            String copilotPullImage = project.ext.get(Constants.COPILOT_PULL_IMAGE)
            if (copilotPullImage.isEmpty()) {
                return defaultResult
            }
            return TRUE == copilotPullImage.toLowerCase()
        } catch (ExtraPropertiesExtension.UnknownPropertyException e) {
            project.logger.info("Failed to get COPILOT_PULL_IMAGE: ${e.getMessage()}. Default value 'true' will be used.")
        }
        return defaultResult
    }

    private static boolean pullDockerImage(Project project, String copilotTag) {
        ByteArrayOutputStream stdOut = new ByteArrayOutputStream()
        ExecResult execResult = project.exec {
            commandLine SH, DASH_C, "docker pull etendo/$Constants.COPILOT_DOCKER_REPO:$copilotTag"
            standardOutput = stdOut
        }
        String stdOutString = stdOut
        if (execResult.exitValue == 0 && !stdOutString.trim().contains('Image is up to date')) {
            project.logger.info('Docker image updated successfully..')
            return true
        }
        return false
    }

    private static void runNewContainer(String containerName, String port, Project project, String tag) {
        String dockerRunCommand = "docker run --env-file=\$(pwd)/gradle.properties --name ${containerName}  -p ${port}:${port} -v ${project.buildDir.path}/copilot/:/app/ -v \$(pwd)/modules:/modules/ etendo/${Constants.COPILOT_DOCKER_REPO}:${tag}"
        project.exec {
            commandLine SH, DASH_C, dockerRunCommand
        }
    }

    private static void delete_container(Project project, String copilotDockerContainerName) {
        try {
            project.exec {
                commandLine SH, DASH_C, 'docker rm ' + copilotDockerContainerName
            }
        } catch (ExecException e) {
            project.logger.warn('Failed to remove Docker container: ' + e.getMessage())
        }
    }
}
