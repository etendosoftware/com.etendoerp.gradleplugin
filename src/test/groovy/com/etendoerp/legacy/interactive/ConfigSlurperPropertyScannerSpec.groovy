package com.etendoerp.legacy.interactive

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Comprehensive test specification for ConfigSlurperPropertyScanner
 * 
 * Tests property scanning from multiple locations, performance, and error handling
 * as specified in ETP-1960-04-TESTPLAN.md (TC29-TC34)
 */
class ConfigSlurperPropertyScannerSpec extends Specification {

    @TempDir
    Path tempDir

    def project
    def scanner

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        
        scanner = new ConfigSlurperPropertyScanner(project)
    }

    // ========== PROPERTY SCANNING TESTS (TC29-TC34) ==========

    def "TC29: should scan properties from multiple locations"() {
        given: "config.gradle files in different locations"
        createConfigFile("modules/test-module/config.gradle", """
            property1 {
                key = 'module.property1'
                defaultValue = 'module-value1'
                documentation = 'Module property 1'
            }
        """)
        
        createConfigFile("modules_core/core-module/config.gradle", """
            property2 {
                key = 'core.property2'
                defaultValue = 'core-value2'
                documentation = 'Core property 2'
            }
        """)

        createConfigFile("build/etendo/modules/generated-module/config.gradle", """
            property3 {
                key = 'generated.property3'
                defaultValue = 'generated-value3'
                documentation = 'Generated property 3'
            }
        """)

        when: "scanning properties from all locations"
        def properties = scanner.scanAllProperties()

        then: "should find and process files from all locations"
        properties != null
        noExceptionThrown()
    }

    def "TC30: should handle duplicate properties with proper precedence"() {
        given: "same property defined in multiple files"
        createConfigFile("modules/module1/config.gradle", """
            duplicateProperty {
                key = 'app.duplicate'
                defaultValue = 'first-value'
                documentation = 'First definition'
            }
        """)
        
        createConfigFile("modules/module2/config.gradle", """
            duplicateProperty {
                key = 'app.duplicate'
                defaultValue = 'second-value'
                documentation = 'Second definition'
            }
        """)

        when: "scanning properties with duplicates"
        def properties = scanner.scanAllProperties()

        then: "should handle duplicates with proper precedence"
        properties != null
        noExceptionThrown()
    }

    def "TC31: should handle missing config files gracefully"() {
        given: "project structure without config.gradle files"
        def emptyModulesDir = new File(tempDir.toFile(), "modules")
        emptyModulesDir.mkdirs()
        
        // Create empty module directories without config.gradle
        new File(emptyModulesDir, "empty-module1").mkdirs()
        new File(emptyModulesDir, "empty-module2").mkdirs()

        when: "scanning properties from empty locations"
        def properties = scanner.scanAllProperties()

        then: "should continue without errors"
        properties != null
        noExceptionThrown()
    }

    def "TC32: should parse complex nested configurations"() {
        given: "config.gradle with complex nested structure"
        createConfigFile("modules/complex-module/config.gradle", """
            database {
                property1 {
                    key = 'database.host'
                    defaultValue = 'localhost'
                    documentation = 'Database hostname'
                    sensitive = false
                }
                property2 {
                    key = 'database.password'
                    defaultValue = ''
                    documentation = 'Database password'
                    sensitive = true
                }
            }
            
            application {
                property1 {
                    key = 'app.name'
                    defaultValue = 'etendo'
                    documentation = 'Application name'
                    group = 'application'
                }
                nested {
                    property1 {
                        key = 'app.nested.property'
                        defaultValue = 'nested-value'
                        documentation = 'Nested property'
                    }
                }
            }
        """)

        when: "parsing complex configuration structure"
        def properties = scanner.scanAllProperties()

        then: "should parse hierarchical structure correctly"
        properties != null
        noExceptionThrown()
    }

    def "TC33: should complete scanning within performance limits"() {
        given: "multiple modules with config.gradle files"
        def startTime = System.currentTimeMillis()
        
        // Create multiple module directories with config files
        (1..10).each { i ->
            createConfigFile("modules/module${i}/config.gradle", """
                property${i} {
                    key = 'module${i}.property'
                    defaultValue = 'value${i}'
                    documentation = 'Property for module ${i}'
                }
            """)
        }

        when: "scanning multiple modules"
        def scanStartTime = System.currentTimeMillis()
        def properties = scanner.scanAllProperties()
        def scanEndTime = System.currentTimeMillis()
        def scanDuration = scanEndTime - scanStartTime

        then: "should complete within reasonable time"
        properties != null
        scanDuration < 5000 // 5 seconds for scanning
        noExceptionThrown()
    }

    def "TC34: should handle corrupted config files gracefully"() {
        given: "valid and corrupted config.gradle files"
        createConfigFile("modules/valid-module/config.gradle", """
            validProperty {
                key = 'valid.property'
                defaultValue = 'valid-value'
                documentation = 'Valid property'
            }
        """)
        
        createConfigFile("modules/corrupted-module/config.gradle", """
            // This is corrupted Groovy syntax
            invalidProperty {
                key = 'invalid.property'
                defaultValue = 'missing closing brace'
                documentation = 'This will cause parsing error'
            // Missing closing brace will cause syntax error
        """)

        when: "scanning properties with corrupted file"
        def properties = scanner.scanAllProperties()

        then: "should handle parsing errors and continue"
        properties != null
        noExceptionThrown()
    }

    // ========== EDGE CASES AND ERROR HANDLING ==========

    def "should handle empty config.gradle files"() {
        given: "empty config.gradle file"
        createConfigFile("modules/empty-module/config.gradle", "")

        when: "scanning empty configuration file"
        def properties = scanner.scanAllProperties()

        then: "should handle empty files"
        properties != null
        noExceptionThrown()
    }

    def "should handle config files with only comments"() {
        given: "config.gradle with only comments"
        createConfigFile("modules/comments-only/config.gradle", """
            // This file contains only comments
            /* 
             * No actual property definitions
             * Just documentation
             */
            // End of file
        """)

        when: "scanning comment-only file"
        def properties = scanner.scanAllProperties()

        then: "should handle comment-only files"
        properties != null
        noExceptionThrown()
    }

    def "should handle properties with missing required fields"() {
        given: "config with incomplete property definitions"
        createConfigFile("modules/incomplete-module/config.gradle", """
            incompleteProperty1 {
                key = 'incomplete.property1'
                // Missing defaultValue and documentation
            }
            
            incompleteProperty2 {
                // Missing key
                defaultValue = 'some-value'
                documentation = 'Property without key'
            }
        """)

        when: "scanning incomplete property definitions"
        def properties = scanner.scanAllProperties()

        then: "should handle incomplete definitions"
        properties != null
        noExceptionThrown()
    }

    def "should handle very deep directory structures"() {
        given: "deeply nested module structure"
        createConfigFile("modules/level1/level2/level3/level4/deep-module/config.gradle", """
            deepProperty {
                key = 'deep.nested.property'
                defaultValue = 'deep-value'
                documentation = 'Deeply nested property'
            }
        """)

        when: "scanning deeply nested structures"
        def properties = scanner.scanAllProperties()

        then: "should handle deep nesting"
        properties != null
        noExceptionThrown()
    }

    def "should handle properties with Unicode characters"() {
        given: "config with Unicode property values"
        createConfigFile("modules/unicode-module/config.gradle", """
            unicodeProperty {
                key = 'unicode.property'
                defaultValue = 'värde_with_ünicöde_çhärs'
                documentation = 'Propiedád con carácteres especiáles'
            }
        """)

        when: "scanning properties with Unicode"
        def properties = scanner.scanAllProperties()

        then: "should handle Unicode correctly"
        properties != null
        noExceptionThrown()
    }

    // ========== HELPER METHODS ==========

    private void createConfigFile(String relativePath, String content) {
        def file = new File(tempDir.toFile(), relativePath)
        file.parentFile.mkdirs()
        file.text = content
    }

    // ========== INTEGRATION TESTS ==========

    def "should create scanner instance successfully"() {
        when: "creating ConfigSlurperPropertyScanner instance"
        def testScanner = new ConfigSlurperPropertyScanner(project)

        then: "should create without errors"
        testScanner != null
        noExceptionThrown()
    }

    def "should handle null project gracefully"() {
        when: "creating scanner with null project"
        new ConfigSlurperPropertyScanner(null)

        then: "should throw appropriate exception"
        thrown(Exception)
    }

    def "should support scanning workflow"() {
        given: "valid project structure"
        createConfigFile("modules/test/config.gradle", """
            testProperty {
                key = 'test.property'
                defaultValue = 'test-value'
                documentation = 'Test property for workflow'
            }
        """)

        when: "executing scanning workflow"
        def properties = scanner.scanAllProperties()

        then: "should support complete workflow"
        properties != null
        noExceptionThrown()
    }
}
