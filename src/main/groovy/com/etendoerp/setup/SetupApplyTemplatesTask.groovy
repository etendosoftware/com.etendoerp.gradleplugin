package com.etendoerp.setup

import com.etendoerp.setup.applicator.TemplateApplicator
import com.etendoerp.setup.template.Template
import com.etendoerp.setup.template.TemplateResolver
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Main task for applying configuration templates
 * 
 * Usage:
 *   ./gradlew setup.applyTemplates                              (interactive mode)
 *   ./gradlew setup.applyTemplates --template=copilot           (from resources)
 *   ./gradlew setup.applyTemplates --file=/path/to/template     (from file)
 *   ./gradlew setup.applyTemplates --url=https://...            (from URL)
 */
class SetupApplyTemplatesTask extends DefaultTask {

    @Input
    @Optional
    @Option(option = "template", description = "Template name from resources")
    String template

    @Input
    @Optional
    @Option(option = "file", description = "Template from local file path")
    String file

    @Input
    @Optional
    @Option(option = "url", description = "Template from remote URL")
    String url

    @Input
    @Optional
    @Option(option = "force", description = "Force execution even if environment is already configured")
    Boolean force = false

    SetupApplyTemplatesTask() {
        group = 'setup'
        description = 'Apply configuration templates to the project'
    }

    @TaskAction
    void execute() {
        try {
            // Check if environment is already configured
            if (!force) {
                validateCleanEnvironment()
            }
            
            // Resolve template from the specified source
            Template resolvedTemplate = TemplateResolver.resolve(project, template, file, url)
            
            if (!resolvedTemplate) {
                throw new IllegalStateException("Failed to resolve template")
            }
            
            project.logger.info("Resolved template: ${resolvedTemplate}")
            
            // Apply the template
            TemplateApplicator.apply(project, resolvedTemplate)
            
            // Execute setup task to apply changes
            project.logger.lifecycle("\n" + "═" * 60)
            project.logger.lifecycle("Executing 'setup' task to apply changes...")
            project.logger.lifecycle("═" * 60)
            
            try {
                def gradlewCommand = System.getProperty('os.name').toLowerCase().contains('windows') ? 'gradlew.bat' : './gradlew'
                def gradlewFile = project.file(gradlewCommand)
                
                if (!gradlewFile.exists()) {
                    project.logger.warn("⚠ Gradle wrapper not found, skipping setup execution")
                    project.logger.warn("  Please run './gradlew setup' manually to apply changes")
                } else {
                    def result = project.exec {
                        workingDir project.projectDir
                        commandLine gradlewCommand, 'setup', '--console=plain'
                        ignoreExitValue = true
                    }
                    
                    if (result.exitValue == 0) {
                        project.logger.lifecycle("\n✓ Setup completed successfully")
                    } else {
                        throw new GradleException("Setup task failed. The template was applied but setup encountered errors.")
                    }
                }
            } catch (GradleException e) {
                // Re-throw GradleException (from setup failure)
                throw e
            } catch (Exception setupEx) {
                project.logger.error("✗ Failed to execute setup task: ${setupEx.message}")
                throw new GradleException("Failed to execute setup task after applying template. Run './gradlew setup' manually.", setupEx)
            }
            
        } catch (Exception e) {
            project.logger.error("Failed to apply template: ${e.message}", e)
            throw e
        }
    }

    /**
     * Validates that the environment is clean and ready for template application
     * Checks if database exists by attempting to connect to it
     */
    private void validateCleanEnvironment() {
        File gradleProps = project.file('gradle.properties')
        
        if (!gradleProps.exists()) {
            // No gradle.properties means clean environment
            project.logger.lifecycle("✓ No gradle.properties found, environment is clean")
            return
        }
        
        Properties props = new Properties()
        gradleProps.withInputStream { props.load(it) }
        
        // Check if database connection properties are configured
        String dbSid = props.getProperty('bbdd.sid')
        String dbSystemUser = props.getProperty('bbdd.systemUser')
        String dbSystemPassword = props.getProperty('bbdd.systemPassword')
        String dbUrl = props.getProperty('bbdd.url')
        String dbHost = props.getProperty('bbdd.rdbms', 'localhost')
        String dbPort = props.getProperty('bbdd.port', '5432')
        
        if (!dbSid || !dbSystemUser) {
            // No database configuration, environment is clean
            project.logger.lifecycle("✓ No database configuration found, environment is clean")
            return
        }
        
        // Build connection URL if not provided
        if (!dbUrl) {
            dbUrl = "jdbc:postgresql://${dbHost}:${dbPort}/${dbSid}"
        }
        
        // Try to establish connection to verify if database exists
        project.logger.lifecycle("Checking if database '${dbSid}' exists...")
        project.logger.lifecycle("Connection URL: ${dbUrl}")
        project.logger.lifecycle("System User: ${dbSystemUser}")
        
        Connection conn = null
        try {
            // Load PostgreSQL driver using the correct classloader
            Class<?> driverClass = Class.forName('org.postgresql.Driver', true, this.class.classLoader)
            project.logger.lifecycle("PostgreSQL driver loaded successfully")
            
            // Create driver instance directly
            java.sql.Driver driver = (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance()
            
            // Create connection properties
            Properties connectionProps = new Properties()
            connectionProps.setProperty('user', dbSystemUser)
            if (dbSystemPassword) {
                connectionProps.setProperty('password', dbSystemPassword)
            }
            
            // Connect directly using the driver instance (bypass DriverManager)
            conn = driver.connect(dbUrl, connectionProps)
            
            // If we reach here, connection was successful - database exists
            project.logger.error("✗ Database connection successful - database exists!")
            conn.close()
            
            throw new IllegalStateException(
                """
                |
                |═══════════════════════════════════════════════════════════════
                |  ✗ Cannot apply template: Database '${dbSid}' already exists
                |═══════════════════════════════════════════════════════════════
                |
                |This task should only be executed in a CLEAN environment 
                |before database creation to avoid configuration conflicts.
                |
                |Database details:
                |  • Name: ${dbSid}
                |  • Host: ${dbHost}:${dbPort}
                |  • User: ${dbSystemUser}
                |
                |If you want to force execution anyway, use:
                |  ./gradlew setup.applyTemplates --template=<name> --force
                |
                |⚠ WARNING: Using --force may overwrite existing configuration!
                |
                """.stripMargin()
            )
            
        } catch (ClassNotFoundException e) {
            project.logger.warn("⚠ PostgreSQL driver not found, skipping database check")
            project.logger.warn("  Exception: ${e.message}")
            // Driver not available, cannot verify - allow execution
            
        } catch (SQLException e) {
            // Connection failed - database likely doesn't exist or is not accessible
            project.logger.lifecycle("✓ Database connection failed (database doesn't exist or is not accessible)")
            project.logger.lifecycle("  SQL State: ${e.getSQLState()}")
            project.logger.lifecycle("  Error Code: ${e.getErrorCode()}")
            project.logger.lifecycle("  Message: ${e.message}")
            // This is expected for a clean environment, allow execution
            
        } finally {
            if (conn != null) {
                try {
                    conn.close()
                } catch (SQLException ignored) {
                }
            }
        }
    }
}
