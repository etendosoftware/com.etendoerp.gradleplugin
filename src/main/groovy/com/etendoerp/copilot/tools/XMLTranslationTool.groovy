package com.etendoerp.copilot.tools

import com.etendoerp.copilot.Constants
import org.gradle.api.Project
import org.gradle.internal.resource.transport.http.HttpRequestException

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class XMLTranslationTool {
    static final String TOOL_FILE_NAME = "XMLTranslationTool.py"

    static void load(Project project) {
        project.tasks.register("copilot.translate") {
            dependsOn({ project.tasks.named("copilotEnvironmentVerification") })
            doLast {
                ToolsUtils.verifyModuleExistsInSources(project, (String) project.findProperty(Constants.ARG_PROPERTY))
                ToolsUtils.verifyCopilotAndToolAreInstalled(project, TOOL_FILE_NAME)

                String translationQuestion = "Translate the xml files from this relative path: " +
                        "modules/${project.findProperty(Constants.ARG_PROPERTY)}/referencedata/translation/"
                String translationURL = "http://0.0.0.0:${project.ext.get(Constants.COPILOT_PORT_PROPERTY)}/question"

                project.logger.info("* Translating...")
                HttpClient client = HttpClient.newHttpClient()
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(translationURL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"${translationQuestion}\"}"))
                        .build()

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
                int httpCode = response.statusCode()
                String responseBody = response.body()

                if (httpCode >= 400) {
                    throw new HttpRequestException("* Translation failed with status code ${httpCode}: ${responseBody}", null)
                } else {
                    project.logger.lifecycle(responseBody)
                }
            }
        }
    }
}
