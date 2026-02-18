package com.etendoerp.autoconfig

import org.gradle.api.Project

interface AutoConfigurator {
    /**
     * Unique name of the configurator (e.g., 'copilot')
     * @return name
     */
    String getName()

    /**
     * Description to be shown when listing available configurators
     * @return description
     */
    String getDescription()

    /**
     * The configuration logic to execute.
     * @param project The gradle project context
     */
    void configure(Project project)
}
