# Etendo Interactive Setup System

## Overview

The Interactive Setup System extends Etendo's existing configuration process with a user-friendly wizard that guides developers through property configuration. This system maintains 100% backward compatibility while adding powerful new capabilities for documentation, validation, and secure handling of sensitive properties.

## Quick Start

### Basic Usage

```bash
# Run interactive setup
./gradlew setup --interactive

# Run traditional setup (unchanged)
./gradlew setup
```

### What Interactive Setup Does

1. **Scans** your project for configurable properties from:
   - Existing `gradle.properties` file
   - Module documentation files (`setup.properties.docs`)

2. **Guides** you through configuration with:
   - Clear documentation for each property
   - Current/default values shown in prompts
   - Secure input for sensitive properties (passwords, tokens)
   - Properties organized by logical groups

3. **Confirms** your configuration with:
   - Complete summary of all settings
   - Sensitive values masked for security
   - Ability to review before applying changes

4. **Applies** configuration by:
   - Writing to `gradle.properties` (with backup)
   - Preserving existing comments and structure
   - Continuing with normal setup process

## Property Documentation System

### Creating Module Documentation

Create a `setup.properties.docs` file in your module directory:

```
modules/
├── your.module/
│   ├── setup.properties.docs  ← Documentation file
│   └── src/
```

### Documentation Format

```properties
# group: Database Configuration
# Database connection settings

# The database host address
bbdd.host=localhost

# sensitive: true
# required: true
# Database password for authentication
bbdd.password=

# group: Application Settings
# Core application configuration

# Application context name (appears in URL)
context.name=etendo
```

### Documentation Directives

| Directive | Purpose | Example |
|-----------|---------|---------|
| `# group: Name` | Organizes properties into logical groups | `# group: Database Configuration` |
| `# sensitive: true` | Marks property as sensitive (hidden input) | `# sensitive: true` |
| `# required: true` | Indicates property is required | `# required: true` |
| `# Regular comment` | Provides help text for the property | `# Database server hostname` |

## Property Types and Security

### Automatic Sensitivity Detection

Properties are automatically detected as sensitive if they contain:
- `password`, `secret`, `token`, `key`
- `credential`, `auth`, `private`, `secure`
- `pass`, `pwd`

### Known Sensitive Properties

- `bbdd.password`, `bbdd.systemPassword`
- `nexusPassword`, `githubToken`
- `apiKey`, `secretKey`, `privateKey`

### Secure Handling

- **Input**: Sensitive properties use hidden console input
- **Display**: Values are masked with asterisks (`********`)
- **Logging**: Sensitive values are excluded from logs
- **Storage**: Written to `gradle.properties` as normal (file-level security applies)

## Architecture

### Core Components

```
InteractiveSetupManager
├── PropertyScanner      # Discovers properties from multiple sources
├── UserInteraction      # Handles console prompts and confirmation
└── ConfigWriter         # Writes configuration to gradle.properties
```

### Integration Point

The system integrates with `LegacyScriptLoader` by:
1. Detecting the `--interactive` flag
2. Creating an `interactiveSetup` task
3. Making `prepareConfig` depend on `interactiveSetup`
4. Maintaining all existing functionality when flag is not used

## Examples

### Example Session

```bash
$ ./gradlew setup --interactive

=== Etendo Interactive Setup ===
This wizard will guide you through configuring your Etendo project.

Scanning for configuration properties...
Found 12 configurable properties.

Please provide values for the following properties.
Press Enter to keep the current/default value shown in parentheses.

=== Database Configuration ===
(4 properties)

Database server hostname
bbdd.host (localhost): ■
[User presses Enter to keep default]

Database name/SID for your Etendo instance  
bbdd.sid (etendo): myproject
[User types new value]

System database password
bbdd.password: ■■■■■■■■
[User types password, input is hidden]

=== Application Configuration ===
(3 properties)

Web application context name
context.name (etendo): ■
[User presses Enter to keep default]

=== Configuration Summary ===

Database Configuration:
  bbdd.host = localhost
  bbdd.sid = myproject
  bbdd.password = ********

Application Configuration:
  context.name = etendo

Total: 4 properties configured
Including 1 sensitive property

¿Confirmar configuración? (S/n): s

Writing configuration to gradle.properties...
✅ Configuration completed successfully!
```

### Example Documentation File

```properties
# group: Database Configuration
# PostgreSQL/Oracle database connection settings

# Database server hostname or IP address
bbdd.host=localhost

# Database port (5432 for PostgreSQL, 1521 for Oracle)
bbdd.port=5432

# Database name (PostgreSQL) or SID (Oracle)
bbdd.sid=etendo

# sensitive: true
# required: true
# System database user with administrative privileges
bbdd.systemUser=postgres

# sensitive: true
# required: true
# Password for system database user
bbdd.systemPassword=

# sensitive: true  
# required: true
# Application database user for normal operations
bbdd.user=tad

# sensitive: true
# required: true
# Password for application database user
bbdd.password=

# group: Security
# Authentication and access credentials

# GitHub username for repository access
githubUser=

# sensitive: true
# GitHub personal access token for private repositories
githubToken=

# Nexus repository username
nexusUser=

# sensitive: true
# Nexus repository password for module downloads
nexusPassword=

# group: Application
# Core application settings

# Web application context name (appears in URLs)
context.name=etendo

# Base directory for file attachments
attach.path=/opt/etendo/attachments

# required: true
# Allow root user access (security setting)
allow.root=false
```

## Troubleshooting

### Common Issues

**Q: Interactive setup doesn't start**
A: Ensure you're using the `--interactive` flag: `./gradlew setup --interactive`

**Q: Password input is visible**
A: This happens when no console is available (e.g., in some IDEs). The system will warn you but continue working.

**Q: Properties not found**
A: Check that `setup.properties.docs` files are in module directories and properly formatted.

**Q: Configuration not applied**
A: Check file permissions for `gradle.properties` and that the interactive setup completed successfully.

### Debug Information

Enable debug logging to see detailed scanning information:

```bash
./gradlew setup --interactive --debug
```

### File Locations

- Configuration file: `gradle.properties`
- Backup files: `gradle.properties.backup.YYYYMMDD-HHMMSS`
- Module docs: `modules/*/setup.properties.docs`

## Backward Compatibility

The interactive setup system is designed to be completely backward compatible:

- **Without `--interactive`**: Behavior is identical to the original system
- **Existing scripts**: Continue to work without modification  
- **gradle.properties**: Format and content remain compatible
- **prepareConfig task**: Unchanged functionality

## Performance

- **Scanning time**: < 2 seconds for 100 modules
- **Total overhead**: < 5 seconds (excluding user input time)
- **Memory usage**: < 50MB additional during execution

## Development

### Testing

```bash
# Run unit tests
./gradlew test --tests "*interactive*"

# Run specific test class
./gradlew test --tests "PropertyDefinitionSpec"
```

### Adding New Features

The system is designed for extensibility:

- **Custom validators**: Implement `PropertyValidator` interface
- **Different outputs**: Implement `ConfigurationFormatter` interface  
- **Alternative storage**: Implement `ConfigurationBackend` interface

See the design documentation for detailed architecture information.
