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
    public static final String EQUAL = '='

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
        // edit the file gradle.properties and replace the . in key names with _ . Remember that the keys are the text before the = sign.
        // If there is a . in the value it not need to be replaced.
        String pwd
        ByteArrayOutputStream stdOut = new ByteArrayOutputStream()
        project.exec {
            commandLine SH, DASH_C, 'pwd'
            stdOut
        }
        pwd = stdOut
        // open the file
        File file = new File(pwd + 'gradle.properties')
        // read the file and iterate over the lines, replace the . in the key names with _ and save in a new file
        File newFile = new File(pwd + 'copilot.properties')
        if (newFile.exists()) {
            newFile.delete()
        }
        newFile.createNewFile()
        // iterate over the lines and replace the . in the key names with _
        newFile.withWriter { writer ->
            file.eachLine { line ->
                if (line.contains(EQUAL)) {
                    String[] split = line.split(EQUAL, 2)
                    String key = split[0].replaceAll('\\.', '_')
                    String value = ''
                    if (split.length > 1) {
                        value = split[1]
                    }
                    writer.writeLine(key + EQUAL + value)
                } else {
                    writer.writeLine(line)
                }
            }
        }
        // run the docker container
        String dockerRunCommand = "docker run --env-file=\$(pwd)/copilot.properties --name ${containerName}  -p ${port}:${port} -v ${project.buildDir.path}/copilot/:/app/ -v \$(pwd)/modules:/modules/ etendo/${Constants.COPILOT_DOCKER_REPO}:${tag}"
        project.exec {
            commandLine SH, DASH_C, dockerRunCommand
        }
        // delete the new file
        newFile.delete()
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
