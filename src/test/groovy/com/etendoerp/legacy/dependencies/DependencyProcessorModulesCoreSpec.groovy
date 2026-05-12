/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.legacy.dependencies

import com.etendoerp.dependencies.EtendoCoreDependencies
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.DependencyContainer
import com.etendoerp.legacy.dependencies.container.DependencyType
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class DependencyProcessorModulesCoreSpec extends Specification {

    @TempDir
    File testProjectDir

    def project
    DependencyProcessor processor

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(testProjectDir)
                .build()
        processor = new DependencyProcessor(project, null)
        processor.dependencyContainer = new DependencyContainer(project, null)
    }

    def "validates declared modules core dependency when resolved as Etendo JAR module with AD_MODULE"() {
        given:
        def artifact = artifactDependency('com.test.modulea', jarWithEntries([
                'META-INF/etendo/modules/com.test.modulea/src-db/database/sourcedata/AD_MODULE.xml'
        ]))
        processor.dependencyContainer.etendoDependenciesJarFiles.put('com.test.modulea', artifact)
        project.ext.set(EtendoCoreDependencies.MODULES_CORE_DEPENDENCIES_TO_VALIDATE, ['com.test.modulea'] as Set)

        when:
        processor.validateModulesCoreDependenciesAreEtendoJarModules()

        then:
        noExceptionThrown()
    }

    def "fails when declared modules core dependency was not resolved as Etendo JAR module"() {
        given:
        project.ext.set(EtendoCoreDependencies.MODULES_CORE_DEPENDENCIES_TO_VALIDATE, ['com.test.modulea'] as Set)

        when:
        processor.validateModulesCoreDependenciesAreEtendoJarModules()

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('not an Etendo module JAR')
    }

    def "fails when declared modules core dependency JAR does not contain AD_MODULE"() {
        given:
        def artifact = artifactDependency('com.test.modulea', jarWithEntries([
                'META-INF/etendo/modules/com.test.modulea/README.md'
        ]))
        processor.dependencyContainer.etendoDependenciesJarFiles.put('com.test.modulea', artifact)
        project.ext.set(EtendoCoreDependencies.MODULES_CORE_DEPENDENCIES_TO_VALIDATE, ['com.test.modulea'] as Set)

        when:
        processor.validateModulesCoreDependenciesAreEtendoJarModules()

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('does not contain AD_MODULE.xml')
    }

    def "detects AD_MODULE inside Etendo module JAR"() {
        given:
        def artifact = artifactDependency('com.test.modulea', jarWithEntries([
                'META-INF/etendo/modules/com.test.modulea/src-db/database/sourcedata/AD_MODULE.xml'
        ]))

        expect:
        processor.containsADModuleFile(artifact)
    }

    def "does not detect AD_MODULE when path belongs to a different module"() {
        given:
        def artifact = artifactDependency('com.test.modulea', jarWithEntries([
                'META-INF/etendo/modules/com.test.other/src-db/database/sourcedata/AD_MODULE.xml'
        ]))

        expect:
        !processor.containsADModuleFile(artifact)
    }

    private ArtifactDependency artifactDependency(String moduleName, File jarFile) {
        def artifact = new ArtifactDependency(project)
        artifact.moduleName = moduleName
        artifact.locationFile = jarFile
        artifact.displayName = "com.test:modulea:1.0.0"
        artifact.type = DependencyType.ETENDOJARMODULE
        return artifact
    }

    private File jarWithEntries(List<String> entries) {
        File jarFile = File.createTempFile('module', '.jar', testProjectDir)
        new JarOutputStream(new FileOutputStream(jarFile)).withCloseable { JarOutputStream jar ->
            entries.each { String entryName ->
                jar.putNextEntry(new JarEntry(entryName))
                jar.write('<xml/>'.bytes)
                jar.closeEntry()
            }
        }
        return jarFile
    }
}
