package com.etendoerp.copilot.tools

import com.etendoerp.copilot.Constants
import net.rubygrapefruit.platform.internal.jni.NativeLogger
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.internal.resource.transport.http.HttpRequestException

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

                String translationRequest = "curl -X POST ${translationURL} " +
                        "-H 'Content-Type: application/json' " +
                        "-d '{\"question\":\"${translationQuestion}\"}' " +
                        "--write-out '%{http_code}'"

                def stdout = new ByteArrayOutputStream()
                project.exec {
                    commandLine 'sh', '-c', translationRequest
                    standardOutput = stdout
                }

                def stdOutStr = stdout.toString().trim()
                def httpCode = stdOutStr.substring(stdOutStr.length() - 3).toInteger()
                def response = stdOutStr.substring(0, stdOutStr.length() - 3)
                if (httpCode >= 400) {
                    throw new HttpRequestException("* Translation attempt failed with status code ${httpCode}: ${response}", null)
                } else {
                    project.logger.log(LogLevel.LIFECYCLE, response)
                }
            }
        }
    }
}
