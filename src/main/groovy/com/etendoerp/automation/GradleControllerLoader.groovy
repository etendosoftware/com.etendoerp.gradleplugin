package com.etendoerp.automation

import io.javalin.Javalin
import org.gradle.api.Project
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class GradleControllerLoader {

    // Shared HTTP client for proxying requests
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    static void load(Project project) {
        // Register setup.web task
        project.tasks.register('setup.web') {
            group = 'setup'
            description = 'Starts the Gradle Control Web UI on port 3851'

            doLast {
                startGradleControllerServer(project)
            }
        }
    }

    static void startGradleControllerServer(Project project) {
        project.logger.lifecycle("Starting Gradle Control API on port 3851...")
        project.logger.lifecycle("Press Ctrl+C to stop the server")
        project.logger.lifecycle("")

        Javalin app = null

        try {
            app = Javalin.create { config ->
                // Enable CORS for all origins
                config.bundledPlugins.enableCors { cors ->
                    cors.addRule { corsConfig ->
                        corsConfig.anyHost()
                    }
                }

                // Serve static files from React app
                config.staticFiles.add { staticFiles ->
                    staticFiles.hostedPath = "/"
                    staticFiles.directory = "/web/dist"
                    staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH
                }

                // Enable Single Page Application mode (for React Router)
                config.spaRoot.addFile("/", "/web/dist/index.html", io.javalin.http.staticfiles.Location.CLASSPATH)
            }.start(3851)

            project.logger.lifecycle("Gradle Control API started on port 3851")
            project.logger.lifecycle("Web UI: http://localhost:3851")
            project.logger.lifecycle("API Endpoint: POST http://localhost:3851/api/execute")
            project.logger.lifecycle("Request format: {\"command\":\"taskName\",\"args\":{}}")
            project.logger.lifecycle("")

            // Login proxy: forward /sws/login to local Etendo backend
            def targetBase = "http://localhost:8080/etendo"
            app.post("/sws/login") { ctx -> proxyToBackend(ctx, project, targetBase) }
            app.get("/sws/login") { ctx -> proxyToBackend(ctx, project, targetBase) }
            // Copilot assistants proxy: forward to /sws/copilot/assistants
            app.get("/api/erp/copilot/assistants") { ctx ->
                proxyToBackend(ctx, project, targetBase, "/sws/copilot/assistants")
            }
            app.post("/api/erp/copilot/assistants") { ctx ->
                proxyToBackend(ctx, project, targetBase, "/sws/copilot/assistants")
            }
            // Copilot question proxy: forward to /sws/copilot/aquestion
            app.get("/api/erp/copilot/aquestion") { ctx ->
                proxyToBackend(ctx, project, targetBase, "/sws/copilot/aquestion")
            }
            app.post("/api/erp/copilot/aquestion") { ctx ->
                proxyToBackend(ctx, project, targetBase, "/sws/copilot/aquestion")
            }

            // Open browser automatically
            openBrowser("http://localhost:3851", project)

            // Initialize services
            def configService = new ConfigurationService(project)
            def dockerProjectName = (project.findProperty("context.name") ?: "etendo").toString()
            def dockerService = new DockerService(project, dockerProjectName)

            // Endpoint: GET /api/config - Get all configurations from config.gradle files
            app.get("/api/config") { ctx ->
                try {
                    def configurations = configService.readAllConfigurations()
                    ctx.json([
                        success: true,
                        data: configurations
                    ])
                } catch (Exception e) {
                    project.logger.error("Error reading configurations: ${e.message}", e)
                    ctx.status(500).json([
                        success: false,
                        error: "Error reading configurations: ${e.message}"
                    ])
                }
            }

            // Endpoint: GET /api/config/groups - Get configurations grouped by category
            app.get("/api/config/groups") { ctx ->
                try {
                    def categorized = configService.readCategorizedConfigurations()
                    ctx.json([
                        success: true,
                        data: categorized
                    ])
                } catch (Exception e) {
                    project.logger.error("Error reading categorized configurations: ${e.message}", e)
                    e.printStackTrace()
                    ctx.status(500).json([
                        success: false,
                        error: e.message?.toString() ?: "Error reading configurations"
                    ])
                }
            }

            // Endpoint: GET /api/config/modules - Get configurations grouped by module
            app.get("/api/config/modules") { ctx ->
                try {
                    def byModule = configService.readConfigurationsByModule()
                    ctx.json([
                        success: true,
                        data: byModule
                    ])
                } catch (Exception e) {
                    project.logger.error("Error reading configurations by module: ${e.message}", e)
                    ctx.status(500).json([
                        success: false,
                        error: "Error reading configurations: ${e.message}"
                    ])
                }
            }

            // Endpoint: GET /api/config/stats - Get configuration statistics
            app.get("/api/config/stats") { ctx ->
                try {
                    def stats = configService.getConfigurationStats()
                    ctx.json([
                        success: true,
                        data: stats
                    ])
                } catch (Exception e) {
                    project.logger.error("Error getting configuration stats: ${e.message}", e)
                    ctx.status(500).json([
                        success: false,
                        error: "Error getting stats: ${e.message}"
                    ])
                }
            }

            // Endpoint: POST /api/config - Save configurations to gradle.properties
            app.post("/api/config") { ctx ->
                try {
                    def requestBody = ctx.body()
                    def json = new groovy.json.JsonSlurper().parseText(requestBody)

                    if (!json.configurations) {
                        ctx.status(400).json([
                            success: false,
                            error: "Missing 'configurations' parameter"
                        ])
                        return
                    }

                    // Save configurations (validation happens inside)
                    def result = configService.saveConfigurations(json.configurations)

                    if (result.success) {
                        ctx.json(result)
                    } else {
                        // Check if it's a validation error or server error
                        if (result.validationErrors) {
                            ctx.status(400).json(result)
                        } else {
                            ctx.status(500).json(result)
                        }
                    }

                } catch (Exception e) {
                    project.logger.error("Error saving configurations: ${e.message}", e)
                    ctx.status(500).json([
                        success: false,
                        error: "Error saving configurations: ${e.message}"
                    ])
                }
            }

            // ========== DOCKER ENDPOINTS ==========

            // Endpoint: GET /api/docker/containers - List all containers
            app.get("/api/docker/containers") { ctx ->
                ctx.json(dockerService.listContainers())
            }

            // Endpoint: POST /api/docker/start - Start all containers
            app.post("/api/docker/start") { ctx ->
                ctx.json(dockerService.startContainers())
            }

            // Endpoint: POST /api/docker/stop - Stop all containers
            app.post("/api/docker/stop") { ctx ->
                ctx.json(dockerService.stopContainers())
            }

            // Endpoint: POST /api/docker/restart - Restart all containers
            app.post("/api/docker/restart") { ctx ->
                ctx.json(dockerService.restartContainers())
            }

            // Endpoint: POST /api/docker/container/{service}/start - Start specific container
            app.post("/api/docker/container/{service}/start") { ctx ->
                def service = ctx.pathParam("service")
                ctx.json(dockerService.startContainer(service))
            }

            // Endpoint: POST /api/docker/container/{service}/stop - Stop specific container
            app.post("/api/docker/container/{service}/stop") { ctx ->
                def service = ctx.pathParam("service")
                ctx.json(dockerService.stopContainer(service))
            }

            // Endpoint: POST /api/docker/container/{service}/restart - Restart specific container
            app.post("/api/docker/container/{service}/restart") { ctx ->
                def service = ctx.pathParam("service")
                ctx.json(dockerService.restartContainer(service))
            }

            // Endpoint: GET /api/docker/logs - Get logs from all containers (last hour)
            app.get("/api/docker/logs") { ctx ->
                def since = ctx.queryParam("since") ?: "1h"
                ctx.json(dockerService.getLogs(since))
            }

            // Endpoint: GET /api/docker/container/{service}/logs - Get logs for specific container
            app.get("/api/docker/container/{service}/logs") { ctx ->
                def service = ctx.pathParam("service")
                def since = ctx.queryParam("since") ?: "1h"
                def tail = ctx.queryParam("tail")?.toInteger() ?: 500
                ctx.json(dockerService.getContainerLogs(service, since, tail))
            }

            // Endpoint: GET /api/docker/container/{service}/inspect - Inspect specific container
            app.get("/api/docker/container/{service}/inspect") { ctx ->
                def service = ctx.pathParam("service")
                ctx.json(dockerService.inspectContainer(service))
            }

            // Endpoint: GET /api/docker/stats - Get container statistics
            app.get("/api/docker/stats") { ctx ->
                ctx.json(dockerService.getStats())
            }

            // Endpoint: GET /api/docker/config - Get Docker Compose configuration
            app.get("/api/docker/config") { ctx ->
                ctx.json(dockerService.getConfig())
            }

            // Endpoint: POST /api/docker/pull - Pull latest images
            app.post("/api/docker/pull") { ctx ->
                ctx.json(dockerService.pullImages())
            }

            // Endpoint: POST /api/docker/remove - Remove stopped containers
            app.post("/api/docker/remove") { ctx ->
                ctx.json(dockerService.removeContainers())
            }

            // Endpoint: POST /api/docker/command - Execute custom Docker command
            app.post("/api/docker/command") { ctx ->
                try {
                    def requestBody = ctx.body()
                    def json = new groovy.json.JsonSlurper().parseText(requestBody)

                    if (!json.command) {
                        ctx.status(400).json([
                            success: false,
                            error: "Missing 'command' parameter"
                        ])
                        return
                    }

                    ctx.json(dockerService.executeCustomCommand(json.command.toString()))
                } catch (Exception e) {
                    ctx.status(500).json([
                        success: false,
                        error: "Error executing command: ${e.message}"
                    ])
                }
            }

            // ========== GRADLE ENDPOINTS ==========

            // Endpoint: POST /api/execute with JSON body: {"command":"smartbuild","args":{}}
            app.post("/api/execute") { ctx ->
                try {
                    // Parse JSON request
                    def requestBody = ctx.body()
                    def json = new groovy.json.JsonSlurper().parseText(requestBody)

                    String command = json.command
                    if (!command) {
                        ctx.json([
                            success: false,
                            output: "",
                            error: "Missing 'command' parameter"
                        ])
                        return
                    }

                    // Execute Gradle task synchronously
                    def response = executeGradleTask(project, command)
                    ctx.json(response)

                } catch (Exception e) {
                    ctx.json([
                        success: false,
                        output: "",
                        error: "Error parsing request: ${e.message}"
                    ])
                }
            }

            // Keep the server running until interrupted
            final Javalin finalApp = app

            // Add shutdown hook
            Thread shutdownHook = new Thread({
                project.logger.lifecycle("\nShutting down Gradle Control API...")
                try {
                    if (finalApp != null) {
                        finalApp.stop()
                        project.logger.lifecycle("Gradle Control API stopped successfully")
                    }
                } catch (Exception e) {
                    project.logger.error("Error stopping API: ${e.message}")
                }
            })
            Runtime.getRuntime().addShutdownHook(shutdownHook)

            // Keep the task alive
            project.logger.lifecycle("Server is running. Press Ctrl+C to stop.")
            Thread.currentThread().join()

        } catch (InterruptedException e) {
            project.logger.lifecycle("\nGradle Control API interrupted")
            Thread.currentThread().interrupt()
        } catch (Exception e) {
            project.logger.error("Error starting Gradle Control API: ${e.message}", e)
            throw e
        } finally {
            if (app != null) {
                try {
                    app.stop()
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Proxies /sws/login to the Etendo backend (default http://localhost:8080/etendo)
     */
    static void proxyToBackend(io.javalin.http.Context ctx, Project project, String targetBase, String overridePath = null) {
        try {
            String query = ctx.queryString() ? "?${ctx.queryString()}" : ""
            String targetPath = overridePath ?: ctx.path()
            URI targetUri = URI.create("${targetBase}${targetPath}${query}")

            byte[] bodyBytes = ctx.bodyAsBytes()
            String method = ctx.method().toString()

            def builder = HttpRequest.newBuilder()
                    .uri(targetUri)
                    .timeout(Duration.ofSeconds(30))
                    .method(method, bodyBytes?.length ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes) : HttpRequest.BodyPublishers.noBody())

            // Copy headers except hop-by-hop / restricted ones
            def skipHeaders = ["host", "content-length", "connection", "transfer-encoding",
                               "upgrade", "proxy-connection", "te", "keep-alive", "trailer"]
            ctx.headerMap().each { k, v ->
                if (!skipHeaders.contains(k.toLowerCase())) {
                    builder.header(k, v)
                }
            }

            HttpResponse<byte[]> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())

            // Return response with headers (skip hop-by-hop)
            response.headers().map().each { k, vals ->
                if (!["transfer-encoding", "content-length"].contains(k.toLowerCase())) {
                    vals.each { ctx.header(k, it) }
                }
            }

            ctx.status(response.statusCode())
            ctx.result(new ByteArrayInputStream(response.body()))

        } catch (Exception e) {
            project.logger.error("Error proxying /sws/login: ${e.message}", e)
            ctx.status(502).json([
                success: false,
                error  : "Proxy error: ${e.message}"
            ])
        }
    }

    static Map executeGradleTask(Project project, String taskName) {
        GradleConnector connector = GradleConnector.newConnector()
        connector.forProjectDirectory(project.rootDir)

        ProjectConnection connection = null
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream()

        try {
            // Connect
            connection = connector.connect()

            // Configure the execution
            BuildLauncher build = connection.newBuild()
            build.forTasks(taskName)

            // Capture output
            build.setStandardOutput(outputStream)
            build.setStandardError(errorStream)

            project.logger.info(">>> Starting task: ${taskName}")

            // Execute (this blocks until the build finishes)
            build.run()

            project.logger.info(">>> Task finished successfully.")

            // Combine output and error streams
            String output = outputStream.toString() + errorStream.toString()

            return [
                success: true,
                output: output,
                error: ""
            ]

        } catch (Exception e) {
            project.logger.error("!!! Error executing task: ${e.message}")

            // Combine output and error streams
            String output = outputStream.toString() + errorStream.toString()

            return [
                success: false,
                output: output,
                error: "exit status 1"
            ]

        } finally {
            // Always close the connection
            if (connection != null) {
                try {
                    connection.close()
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Opens the default browser with the given URL
     */
    static void openBrowser(String url, Project project) {
        try {
            String os = System.getProperty("os.name").toLowerCase()

            if (os.contains("win")) {
                // Windows
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler ${url}")
            } else if (os.contains("mac")) {
                // macOS
                Runtime.getRuntime().exec("open ${url}")
            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux/Unix
                Runtime.getRuntime().exec("xdg-open ${url}")
            } else {
                project.logger.warn("Could not detect OS to open browser. Please open ${url} manually.")
            }

            project.logger.lifecycle("Opening browser at ${url}")
        } catch (Exception e) {
            project.logger.warn("Could not open browser automatically: ${e.message}")
            project.logger.lifecycle("Please open ${url} manually in your browser.")
        }
    }
}
