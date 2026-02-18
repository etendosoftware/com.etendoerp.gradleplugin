package com.etendoerp.automation

import org.gradle.api.Project

/**
 * Service to manage Docker containers for Etendo
 * Provides wrapper methods for common Docker Compose operations
 */
class DockerService {

    private final Project project
    private final String projectName

    DockerService(Project project, String projectName = "etendo") {
        this.project = project
        this.projectName = projectName
    }

    /**
     * Lists all containers for the project
     * @return Map with command output
     */
    Map<String, Object> listContainers() {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "ps", "-a"],
            "List containers"
        )
    }

    /**
     * Starts all containers
     * @return Map with command output
     */
    Map<String, Object> startContainers() {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "up", "-d"],
            "Start containers"
        )
    }

    /**
     * Stops all containers
     * @return Map with command output
     */
    Map<String, Object> stopContainers() {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "down"],
            "Stop containers"
        )
    }

    /**
     * Restarts all containers
     * @return Map with command output
     */
    Map<String, Object> restartContainers() {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "restart"],
            "Restart containers"
        )
    }

    /**
     * Starts a specific container
     * @param serviceName Name of the service to start
     * @return Map with command output
     */
    Map<String, Object> startContainer(String serviceName) {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "start", serviceName],
            "Start container: ${serviceName}"
        )
    }

    /**
     * Stops a specific container
     * @param serviceName Name of the service to stop
     * @return Map with command output
     */
    Map<String, Object> stopContainer(String serviceName) {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "stop", serviceName],
            "Stop container: ${serviceName}"
        )
    }

    /**
     * Restarts a specific container
     * @param serviceName Name of the service to restart
     * @return Map with command output
     */
    Map<String, Object> restartContainer(String serviceName) {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "restart", serviceName],
            "Restart container: ${serviceName}"
        )
    }

    /**
     * Gets logs from the last hour for all containers
     * @param since Time period (default: 1h)
     * @return Map with logs
     */
    Map<String, Object> getLogs(String since = "1h") {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "logs", "--since=${since}", "--tail=500"],
            "Get logs from last ${since}"
        )
    }

    /**
     * Gets logs for a specific container
     * @param serviceName Name of the service
     * @param since Time period (default: 1h)
     * @param tail Number of lines (default: 500)
     * @return Map with logs
     */
    Map<String, Object> getContainerLogs(String serviceName, String since = "1h", int tail = 500) {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "logs", "--since=${since}", "--tail=${tail}", serviceName],
            "Get logs for ${serviceName} from last ${since}"
        )
    }

    /**
     * Follows logs in real-time for a specific container
     * @param serviceName Name of the service
     * @param tail Number of initial lines to show
     * @return Map with command info
     */
    Map<String, Object> followLogs(String serviceName, int tail = 100) {
        return [
            success: true,
            message: "To follow logs, use: docker compose -p ${projectName} logs -f --tail=${tail} ${serviceName}",
            command: "docker compose -p ${projectName} logs -f --tail=${tail} ${serviceName}"
        ]
    }

    /**
     * Gets Docker Compose configuration
     * @return Map with configuration
     */
    Map<String, Object> getConfig() {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "config"],
            "Get Docker Compose configuration"
        )
    }

    /**
     * Gets status and stats of containers
     * @return Map with container stats
     */
    Map<String, Object> getStats() {
        return executeDockerCommand(
            ["docker", "stats", "--no-stream", "--format",
             "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}"],
            "Get container statistics"
        )
    }

    /**
     * Inspects a specific container
     * @param serviceName Name of the service
     * @return Map with container details
     */
    Map<String, Object> inspectContainer(String serviceName) {
        // First get the container ID
        def psResult = executeDockerCommand(
            ["docker", "compose", "-p", projectName, "ps", "-q", serviceName],
            "Get container ID for ${serviceName}"
        )

        if (!psResult.success) {
            return psResult
        }

        def containerId = psResult.output.trim()
        if (!containerId) {
            return [
                success: false,
                output: "",
                error: "Container ${serviceName} not found or not running"
            ]
        }

        return executeDockerCommand(
            ["docker", "inspect", containerId],
            "Inspect container: ${serviceName}"
        )
    }

    /**
     * Executes a custom Docker command
     * @param command Command as string (e.g., "docker compose -p etendo ps")
     * @return Map with command output
     */
    Map<String, Object> executeCustomCommand(String command) {
        def parts = command.split("\\s+")
        return executeDockerCommand(
            parts.toList(),
            "Custom command: ${command}"
        )
    }

    /**
     * Pulls latest images
     * @return Map with command output
     */
    Map<String, Object> pullImages() {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "pull"],
            "Pull latest Docker images"
        )
    }

    /**
     * Removes stopped containers
     * @return Map with command output
     */
    Map<String, Object> removeContainers() {
        return executeDockerCommand(
            ["docker", "compose", "-p", projectName, "rm", "-f"],
            "Remove stopped containers"
        )
    }

    /**
     * Internal method to execute Docker commands
     */
    private Map<String, Object> executeDockerCommand(List<String> commandParts, String description) {
        try {
            project.logger.info("Executing: ${commandParts.join(' ')}")

            def process = commandParts.execute()
            def output = new StringBuilder()
            def error = new StringBuilder()

            process.consumeProcessOutput(output, error)
            def exitCode = process.waitFor()

            def result = [
                success: exitCode == 0,
                output: output.toString(),
                error: error.toString(),
                command: commandParts.join(' '),
                exitCode: exitCode,
                description: description
            ]

            if (exitCode != 0) {
                project.logger.error("Docker command failed: ${commandParts.join(' ')}")
                project.logger.error("Error: ${error.toString()}")
            }

            return result

        } catch (Exception e) {
            project.logger.error("Error executing Docker command: ${e.message}", e)
            return [
                success: false,
                output: "",
                error: "Exception: ${e.message}",
                command: commandParts.join(' '),
                exitCode: -1,
                description: description
            ]
        }
    }
}
