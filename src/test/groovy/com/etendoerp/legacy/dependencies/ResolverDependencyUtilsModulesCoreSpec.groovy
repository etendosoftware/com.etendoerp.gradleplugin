package com.etendoerp.legacy.dependencies

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class ResolverDependencyUtilsModulesCoreSpec extends Specification {

    @TempDir
    File testProjectDir

    def project

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(testProjectDir)
                .build()
        project.pluginManager.apply('java')
    }

    def "infers modules core dependency coordinates from source module name"() {
        expect:
        ResolverDependencyUtils.inferModulesCoreDependencyNames('org.openbravo.utility.cleanup.log').containsAll([
                'org.openbravo:utility.cleanup.log',
                'org.openbravo:org.openbravo.utility.cleanup.log',
                'org.openbravo.utility.cleanup:log',
                'org.openbravo.utility.cleanup.log:org.openbravo.utility.cleanup.log'
        ])
    }


    def "excludes modules core jar dependency when source module exists"() {
        given:
        new File(testProjectDir, 'modules_core/org.openbravo.utility.cleanup.log').mkdirs()
        def implementation = project.configurations.implementation
        project.dependencies.add('implementation', 'org.openbravo:utility.cleanup.log:1.0.0')
        project.dependencies.add('implementation', 'org.openbravo:other.module:1.0.0')

        when:
        ResolverDependencyUtils.excludeModulesCoreSourceDependencies(project, [implementation])

        then:
        !implementation.dependencies.any { it.group == 'org.openbravo' && it.name == 'utility.cleanup.log' }
        implementation.dependencies.any { it.group == 'org.openbravo' && it.name == 'other.module' }
        implementation.excludeRules.any { it.group == 'org.openbravo' && it.module == 'utility.cleanup.log' }
    }

    def "excludes modules core jar dependency when artifact id is full package"() {
        given:
        new File(testProjectDir, 'modules_core/org.openbravo.utility.cleanup.log').mkdirs()
        def implementation = project.configurations.implementation
        project.dependencies.add('implementation', 'org.openbravo:org.openbravo.utility.cleanup.log:1.0.0')

        when:
        ResolverDependencyUtils.excludeModulesCoreSourceDependencies(project, [implementation])

        then:
        implementation.dependencies.empty
        implementation.excludeRules.any { it.group == 'org.openbravo' && it.module == 'org.openbravo.utility.cleanup.log' }
    }
}
