package com.etendoerp.copilot

import com.etendoerp.copilot.dockerenv.CopilotStart
import com.etendoerp.copilot.dockerenv.CopilotStop
import org.gradle.api.Project

class CopilotLoader {
    static void load(Project project) {
        CopilotEnvironmentVerification.load(project)
        CopilotStart.load(project)
        CopilotStop.load(project)
    }
}
