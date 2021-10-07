package com.etendoerp.gradle.jars

import com.etendoerp.gradle.tests.EtendoSpecification

/**
 * This class should apply the 'etendo_plugin_mockup' to prevent changing
 * the default behavior of the legacy tests.
 */
abstract class EtendoMockupSpecificationTest extends EtendoSpecification{

    final static String MOCKUP_PLUGIN_ID      = "com.etendoerp.mockup.etendo-mockup"
    final static String MOCKUP_PLUGIN_VERSION = "1.0.3-SNAPSHOT"

    def setup() {
        File buildFile = buildFile
        def extraPlugin = """
        plugins {
            id '${MOCKUP_PLUGIN_ID}' version '${MOCKUP_PLUGIN_VERSION}'
        }
        """
        extraPlugin += buildFile.text
        buildFile.text = extraPlugin
    }
}
