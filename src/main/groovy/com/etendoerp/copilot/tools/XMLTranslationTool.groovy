package com.etendoerp.copilot.tools

import com.etendoerp.copilot.Constants
import org.gradle.api.Project

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

                String translationRequest = "wget -S --header=\"Accept-Encoding: gzip, deflate\" " +
                        "--header='Accept-Charset: UTF-8' --header='Content-Type: application/json' " +
                        "--post-data '{\"question\":\"${translationQuestion}\"}' ${translationURL} "

                project.exec {
                    workingDir '.'
                    commandLine 'sh', '-c', translationRequest
                }
            }
        }
    }
}
