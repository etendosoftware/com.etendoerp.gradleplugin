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
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.dependencies

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class EtendoCoreDependenciesSpec extends Specification {

    @TempDir
    File testProjectDir

    def project

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(testProjectDir)
                .build()
        project.pluginManager.apply('java')
    }

    def "loads modules core dependencies from artifacts list"() {
        given:
        writeArtifactsList("""
            List<String> _dependenciesListCOMPILATION = [
              'org.postgresql:postgresql:42.7.8'
            ]
            List<String> _dependenciesListMODULESCORE = [
              'com.test:modulea:1.0.0',
              'com.test:moduleb:2.0.0'
            ]
            ext {
              dependenciesListCOMPILATION = _dependenciesListCOMPILATION
              dependenciesListMODULESCORE = _dependenciesListMODULESCORE
            }
        """)

        expect:
        EtendoCoreDependencies.loadModulesCoreDependenciesFromFile(project) == [
                'com.test:modulea:1.0.0',
                'com.test:moduleb:2.0.0'
        ]
    }

    def "adds modules core dependencies as transitive implementation dependencies"() {
        given:
        writeArtifactsList("""
            List<String> _dependenciesListCOMPILATION = [
              'org.postgresql:postgresql:42.7.8'
            ]
            List<String> _dependenciesListMODULESCORE = [
              'com.test:modulea:1.0.0'
            ]
            ext {
              dependenciesListCOMPILATION = _dependenciesListCOMPILATION
              dependenciesListMODULESCORE = _dependenciesListMODULESCORE
            }
        """)

        when:
        EtendoCoreDependencies.loadModulesCoreDependencies(project)

        then:
        def dependency = project.configurations.implementation.dependencies.find {
            it.group == 'com.test' && it.name == 'modulea' && it.version == '1.0.0'
        }
        dependency != null
        dependency.transitive
        project.ext.get(EtendoCoreDependencies.MODULES_CORE_DEPENDENCIES_TO_VALIDATE) == ['com.test.modulea'] as Set
        project.ext.get(EtendoCoreDependencies.MODULES_CORE_DEPENDENCIES_TO_RESOLVE) == ['com.test:modulea:1.0.0']
    }

    def "source module wins over modules core jar dependency"() {
        given:
        new File(testProjectDir, 'modules_core/com.test.modulea').mkdirs()
        writeArtifactsList("""
            List<String> _dependenciesListCOMPILATION = []
            List<String> _dependenciesListMODULESCORE = [
              'com.test:modulea:1.0.0'
            ]
            ext {
              dependenciesListCOMPILATION = _dependenciesListCOMPILATION
              dependenciesListMODULESCORE = _dependenciesListMODULESCORE
            }
        """)

        when:
        EtendoCoreDependencies.loadModulesCoreDependencies(project)

        then:
        !project.configurations.implementation.dependencies.any {
            it.group == 'com.test' && it.name == 'modulea'
        }
        project.ext.get(EtendoCoreDependencies.MODULES_CORE_DEPENDENCIES_TO_VALIDATE).isEmpty()
        project.ext.get(EtendoCoreDependencies.MODULES_CORE_DEPENDENCIES_TO_RESOLVE).isEmpty()
    }

    def "fails when modules core dependency is also declared as compilation dependency"() {
        given:
        writeArtifactsList("""
            List<String> _dependenciesListCOMPILATION = [
              'com.test:modulea:1.0.0'
            ]
            List<String> _dependenciesListMODULESCORE = [
              'com.test:modulea:2.0.0'
            ]
            ext {
              dependenciesListCOMPILATION = _dependenciesListCOMPILATION
              dependenciesListMODULESCORE = _dependenciesListMODULESCORE
            }
        """)

        when:
        EtendoCoreDependencies.loadModulesCoreDependencies(project)

        then:
        def ex = thrown(GradleException)
        ex.message.contains('declared in both')
    }

    def "fails when modules core dependency declares an explicit extension"() {
        given:
        writeArtifactsList("""
            List<String> _dependenciesListCOMPILATION = []
            List<String> _dependenciesListMODULESCORE = [
              'com.test:modulea:1.0.0@zip'
            ]
            ext {
              dependenciesListCOMPILATION = _dependenciesListCOMPILATION
              dependenciesListMODULESCORE = _dependenciesListMODULESCORE
            }
        """)

        when:
        EtendoCoreDependencies.loadModulesCoreDependencies(project)

        then:
        def ex = thrown(GradleException)
        ex.message.contains('only supports JAR module dependencies')
    }

    def "gets module name when artifact is a suffix of the package"() {
        expect:
        EtendoCoreDependencies.getModuleName('com.etendoerp:analytics.exporter:3.3.0') == 'com.etendoerp.analytics.exporter'
    }

    def "gets module name when artifact is already the full package"() {
        expect:
        EtendoCoreDependencies.getModuleName('com.etendoerp:com.etendoerp.analytics.exporter:3.3.0') == 'com.etendoerp.analytics.exporter'
    }

    def "fails when modules core dependency notation is incomplete"() {
        given:
        writeArtifactsList("""
            List<String> _dependenciesListCOMPILATION = []
            List<String> _dependenciesListMODULESCORE = [
              'com.test:modulea'
            ]
            ext {
              dependenciesListCOMPILATION = _dependenciesListCOMPILATION
              dependenciesListMODULESCORE = _dependenciesListMODULESCORE
            }
        """)

        when:
        EtendoCoreDependencies.loadModulesCoreDependencies(project)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Expected format: 'group:artifact:version'")
    }

    private void writeArtifactsList(String content) {
        new File(testProjectDir, 'artifacts.list.COMPILATION.gradle').text = content.stripIndent()
    }
}
