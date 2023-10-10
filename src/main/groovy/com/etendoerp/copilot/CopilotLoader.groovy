package com.etendoerp.copilot

import com.etendoerp.copilot.configuration.CopilotConfigurationLoader
import com.etendoerp.copilot.dockerenv.CopilotEnvironmentVerification
import com.etendoerp.copilot.dockerenv.CopilotStart
import com.etendoerp.copilot.dockerenv.CopilotStop
import com.etendoerp.copilot.tools.CopilotToolsLoader
import org.gradle.api.Project

class CopilotLoader {
    static void load(Project project) {
        CopilotConfigurationLoader.load(project)
        CopilotEnvironmentVerification.load(project)
        CopilotStart.load(project)
        CopilotStop.load(project)
        CopilotToolsLoader.load(project)
    }
}
