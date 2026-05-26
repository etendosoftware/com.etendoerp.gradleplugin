package com.etendoerp.core

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class CoreMetadataSpec extends Specification {

    @TempDir
    File testProjectDir

    def "detects source core when src-core exists without modules_core"() {
        given:
        new File(testProjectDir, 'src-core').mkdirs()
        def project = ProjectBuilder.builder()
                .withProjectDir(testProjectDir)
                .build()
        def metadata = new CoreMetadata(project, false)
        metadata.project = project

        expect:
        metadata.isCoreInSources()
        metadata.resolveCoreType() == CoreType.SOURCES
    }
}
