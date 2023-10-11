package com.etendoerp.copilot.tools

import com.etendoerp.copilot.Constants
import org.gradle.api.Project
import org.gradle.internal.resource.transport.http.HttpRequestException

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class XMLTranslationTool {
    static final String TOOL_FILE_NAME = "XML_translation_tool.py"

    static void load(Project project) {
        project.tasks.register("copilot.translate") {
            dependsOn({ project.tasks.named("copilotEnvironmentVerification") })
            doLast {
                ToolsUtils.verifyModuleExistsInSources(project, (String) project.findProperty(Constants.PKG_PROPERTY))
                ToolsUtils.verifyCopilotAndToolAreInstalled(project, TOOL_FILE_NAME)

                String translationQuestion = "Translate the content of this file: " +
                        "modules/${project.findProperty(Constants.PKG_PROPERTY)}/referencedata/translation/"
                String translationURL = "http://0.0.0.0:${project.ext.get(Constants.COPILOT_PORT_PROPERTY)}/question"

                project.logger.info("* Attempting translation...")
                HttpClient client = HttpClient.newHttpClient()
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(translationURL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"${translationQuestion}\"}"))
                        .build()

                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
                    int httpCode = response.statusCode()
                    String responseBody = response.body()

                    if (httpCode >= 400) {
                        throw new HttpRequestException("* Translation attempt failed with status code ${httpCode}: ${responseBody}", null)
                    } else {
                        project.logger.lifecycle(responseBody)
                    }
                } catch (Exception e) {
                    project.logger.error(e.toString())
                }
            }
        }
    }
}
