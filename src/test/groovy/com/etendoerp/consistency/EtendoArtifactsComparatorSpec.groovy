package com.etendoerp.consistency

import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.DependencyType
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class EtendoArtifactsComparatorSpec extends Specification {

    def project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    def "loadVersionStatus sets EQUAL, MAJOR, MINOR and UNDEFINED"() {
        given:
        def local = new ArtifactDependency(project, "com.test", "m", "1.0.0")
        local.versionParser = "1.0.0"
        def installed = new ArtifactDependency(project, "com.test", "m", "1.0.0")
        installed.versionParser = "1.0.0"

        when:
        def comparator = new EtendoArtifactsComparator(project, local, installed)
        comparator.loadVersionStatus()

        then:
        comparator.versionStatus == VersionStatus.EQUAL

        when:
        installed.versionParser = "0.9.0"
        comparator.loadVersionStatus()

        then:
        comparator.versionStatus == VersionStatus.MAJOR

        when:
        installed.versionParser = "2.0.0"
        comparator.loadVersionStatus()

        then:
        comparator.versionStatus == VersionStatus.MINOR

        when:
        local.versionParser = null
        comparator.loadVersionStatus()

        then:
        comparator.versionStatus == VersionStatus.UNDEFINED
    }

    def "getArtifactType resolves core vs module vs artifact"() {
        given:
        def local = new ArtifactDependency(project, "com.test", "m", "1.0.0")
        local.versionParser = "1.0.0"
        local.type = DependencyType.ETENDOCOREJAR
        def comparator = new EtendoArtifactsComparator(project, local)

        expect:
        comparator.getArtifactType() == "CORE"

        when:
        local.type = DependencyType.ETENDOJARMODULE

        then:
        comparator.getArtifactType() == "module"

        when:
        local.type = null

        then:
        comparator.getArtifactType() == "artifact"
    }
}
