# Setup Task: Apply Templates

## Overview

The `setup.applyTemplates` task allows you to apply configuration templates to your Etendo project. Templates are predefined configuration files that contain properties, dependencies, and modules.

## Usage

### 1. Interactive Mode (No Parameters)

```bash
./gradlew setup.applyTemplates
```

This will display a list of available templates and prompt you to select one:

```
Select one of the available templates:
1- copilot
2- base
3- production
4- development

You can also use:
  --template=<templateName> (template)
  --file=/path/to/template  (local file)
  --url=https://...         (remote URL)

Enter your selection (1-4):
```

### 2. Using Bundled Templates

```bash
./gradlew setup.applyTemplates --template=<templateName>
```

**Available bundled templates:**
- `copilot` - Configures Etendo Copilot integration
- `base` - Basic PostgreSQL database configuration
- `production` - Production environment settings
- `development` - Development environment settings

**Example:**
```bash
./gradlew setup.applyTemplates --template=copilot
```

### 3. Using Local Template File

```bash
./gradlew setup.applyTemplates --file=/path/to/custom.template
```

**Example:**
```bash
./gradlew setup.applyTemplates --file=/home/user/my-template.template
```

### 4. Using Remote Template URL

```bash
./gradlew setup.applyTemplates --url=https://example.com/templates/custom.template
```

**Example:**
```bash
./gradlew setup.applyTemplates --url=https://cdn.etendo.cloud/templates/production.template
```

## Template Format

Templates use a simple INI-like format with three sections:

```properties
[properties]
key=value
another.key=another.value

[dependencies]
implementation 'group:artifact:version'
testImplementation 'group:artifact:version'

[modules]
group:artifact:version
git::https://github.com/user/repo.git::branch=main
```

### Sections

#### [properties]
Properties are added or updated in `gradle.properties`:
- If a property already exists, it will be updated
- If it doesn't exist, it will be added

#### [dependencies]
Dependencies are added to the `dependencies` block in `build.gradle`:
- If a dependency already exists, it will be skipped
- New dependencies are added with proper indentation

#### [modules]
Modules can be:
- **Artifacts**: `group:artifact:version` (added as implementation dependency)
- **Git repositories**: `git::<url>::branch=<branch>` (cloned to modules directory)

## Example Templates

### Copilot Template
```properties
[properties]
copilot.enabled=true
copilot.port=5005
agent.sync.enabled=true

[dependencies]
implementation 'com.etendoerp:copilot:1.0.0'
implementation 'com.etendoerp:agents:1.0.0'
runtimeOnly 'com.etendoerp:copilot-tools:1.0.0'

[modules]
com.etendoerp:copilot-extras:1.0.0
git::https://github.com/etendosoftware/copilot-custom.git::branch=main
```

### Development Template
```properties
[properties]
environment=development
log.level=DEBUG
debug.enabled=true
hot.reload=true

[dependencies]
testImplementation 'junit:junit:4.13.2'

[modules]
```

## Backups

Before applying any template, backups of the following files are created in `.template-backups/`:
- `gradle.properties.<timestamp>`
- `build.gradle.<timestamp>`

## Output

When applying a template, you'll see output like:

```
Applying template: development
  [properties] -> gradle.properties
*** environment=development
*** log.level=DEBUG
*** debug.enabled=true
*** hot.reload=true
  [dependencies] -> build.gradle
*** testImplementation 'junit:junit:4.13.2'
  [modules]
*** artifact: com.etendoerp:copilot-extras:1.0.0

Template 'development' applied successfully
```

## Creating Custom Templates

To create your own template:

1. Create a file with `.template` extension
2. Add the three sections: `[properties]`, `[dependencies]`, `[modules]`
3. Use the template with `--file` or `--url` option

**Example custom template:**

```properties
# My Custom Template
# Description of what this template does

[properties]
my.custom.property=value
another.property=123

[dependencies]
implementation 'com.example:my-library:1.0.0'

[modules]
com.example:my-module:1.0.0
```

## CI/CD Usage

For automated environments, use the non-interactive modes:

```bash
# In your CI/CD pipeline
./gradlew setup.applyTemplates --url=https://internal.company.com/templates/ci.template
```

## Troubleshooting

### Template not found
If you get an error like "Template 'xyz' not found in resources", ensure:
- The template name is correct (case-sensitive)
- You're using one of the bundled templates: copilot, base, production, development

### File not found
For file-based templates:
- Verify the file path is correct and absolute
- Ensure the file has proper read permissions
- Check that the file extension is `.template`

### URL connection failed
For URL-based templates:
- Verify the URL is accessible
- Check your internet connection
- Ensure the URL returns a valid template file

## See Also

- [SETUP_IMPLEMENTATION_PLAN.md](SETUP_IMPLEMENTATION_PLAN.md) - Technical implementation details
- [ETP-3296](https://etendoproject.atlassian.net/browse/ETP-3296) - Module management feature
