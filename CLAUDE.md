# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java-based CLI tool built with Quarkus that synchronizes Linear issues to Jira projects. It performs one-way synchronization with incremental updates and maintains state to avoid duplicates.

## Build and Development Commands

### Quick Start with JBang (No Build Required)
```bash
# Run directly without building
jbang ljs.java sync --dry-run

# Test API connections
jbang ljs.java test-connection

# Sync with specific team
jbang ljs.java sync --team ENG --verbose

# Sync specific issue (use full --issue flag, not -i which conflicts with JBang)
jbang ljs.java sync --issue JIR-1

# JBang caches dependencies after first run for faster subsequent executions
```

### Building with Maven
```bash
# Clean build
./mvnw clean package

# Development mode with live reload
./mvnw quarkus:dev

# Development mode with arguments
./mvnw quarkus:dev -Dquarkus.args='sync --dry-run --verbose'

# Build native executable
./mvnw package -Dnative
```

### Testing
```bash
# Run all tests
./mvnw test

# Run tests with coverage
./mvnw clean test jacoco:report

# Run only unit tests
./mvnw test -Dtest="*Test"

# Run only integration tests
./mvnw test -Dtest="*IT"
```

### Code Formatting
Uses [jbang-fmt](https://github.com/jbangdev/jbang-fmt) with Eclipse formatter.

```bash
# Format all Java files
./format.sh

# Check formatting without modifying files (for CI)
./format.sh check

# Use a different style (eclipse, google, java, quarkus, spring)
FORMAT_STYLE=quarkus ./format.sh
```

### Running the Application
```bash
# Using JBang (preferred for quick runs)
jbang ljs.java sync --dry-run

# Using packaged JAR
java -jar target/quarkus-app/quarkus-run.jar sync --dry-run

# Test API connections
jbang ljs.java test-connection
# or
java -jar target/quarkus-app/quarkus-run.jar test-connection

# Development mode
./mvnw quarkus:dev -Dquarkus.args='sync --team ENG --dry-run'
```

## Architecture

### State Management
The application maintains a `.syncstate.json` file in the working directory to track previously synced issues and timestamps for incremental synchronization.

## Environment Configuration

Required environment variables:
- `LINEAR_API_TOKEN`: Linear API token
- `JIRA_API_CLOUDID`: Atlassian Cloud ID (preferred) — or `JIRA_API_URL` as alternative
- `JIRA_USERNAME`: Jira username/email  
- `JIRA_API_TOKEN`: Jira API token
- `JIRA_PROJECT_KEY`: Target Jira project key

Optional variables:
- `JIRA_API_URL`: Direct Jira instance URL (alternative to `JIRA_API_CLOUDID`)
- `JIRA_ISSUE_TYPE`: Issue type (default: "Task")
- `JIRA_LINEAR_ID_FIELD`: Custom field ID for Linear issue tracking
- `JIRA_ENABLE_PRIORITY`: Enable priority field mapping (default: false)
- `ATTACHMENT_SYNC_ENABLED`: Enable attachment file synchronization (default: true)
- `ATTACHMENT_DOWNLOAD_TIMEOUT`: Timeout for downloading attachments in seconds (default: 30)
- `ATTACHMENT_MAX_SIZE`: Maximum attachment file size in bytes (default: 10485760 = 10MB)

## Common Development Workflows

### Adding New Command Options
Each action is its own Aesh command class in `cli/boundary` (`SyncCommand`, `TestConnectionCommand`,
`ListIssueTypesCommand`, `ListLinearIssuesCommand`, `ReadLinearIssueCommand`). Add `@Option` fields to the
relevant command. Flags shared by every command belong in the `OutputOptions` mixin.
New commands must be added to `LinearJiraSyncCommand.getCommands()`.

### Extending API Clients
- Linear: Modify GraphQL queries in `LinearService.java` and update response models
- Jira: Add new REST endpoints in `JiraService.java` with proper error handling

### Field Mapping Changes  
Update `IssueTransformer.java` to modify how Linear fields are converted to Jira format.

### Testing Strategy
The project uses WireMock for API client testing and Mockito for unit tests. Each service layer has comprehensive test coverage including error scenarios.

## Java 21+ Coding Standards

### Type Inference
- Use `var` for local variables when the type is obvious from the right-hand side
- Prefer `var` in for-each loops: `for (var issue : issues)`
- Use explicit types when clarity is important or when the type isn't obvious

### Modern Language Features
- **Records**: Use for immutable data classes instead of traditional POJOs
- **Sealed classes**: Use for restricted class hierarchies (e.g., API response types)
- **Pattern matching**: Use switch expressions and pattern matching where applicable
- **Text blocks**: Use for multi-line strings like GraphQL queries or JSON templates

### Collection and Stream APIs
- Use `List.of()`, `Set.of()`, `Map.of()` for immutable collections
- Prefer Stream API for data transformations
- Use `Optional` properly - avoid `.get()`, use `.orElse()`, `.orElseThrow()`, etc.

### String Handling
- Use text blocks for GraphQL queries and multi-line strings
- Prefer `String.format()` or string templates for complex formatting
- Use `String.isBlank()` and `String.strip()` over legacy methods

### Exception Handling
- Use try-with-resources for resource management
- Prefer specific exception types over generic Exception
- Use `Optional` to avoid null checks where appropriate

### Code Style
- Prefer composition over inheritance
- Use method references where they improve readability: `::methodName`
- Keep methods small and focused on single responsibilities
- Use meaningful variable names even with `var`
- Write simple and understandable and beautiful code
- Structure the code using the Boundary Control Entity (BCE/ECB) pattern
- Name packages after their domain responsibilities
- Create package-info.javes for top level packages and write JavaDoc to document the design decisions, intentions and code responsibility
- Do not create single line JavaDoc comments which rephrase the code without adding any additional information
- Capture the package responsibility and not the contents. Do not explain the ECB pattern, focus on domain logic.
- follow the links in JavaDoc to external specifications and use them for code generation 
- keep coarse-grained classes in the boundary package implement procedural business logic in the control package. maintain domain objects, data classes, and entities in the "entity" package
- Avoid writing repetitive: unit tests. Keep only essential tests that verify core functionality without redundancy.
- Avoid using meaningless endings like: *Impl, *Service or *Manager for class names. Name classes after their responsibilities.
- According to "Maximal Cohesion, Minimal Coupling" create smaller classes with dedicated responsibilities
- Do not rephrase code in javadoc. Either describe the "why" or do not comment at all.
- Use interfaces for the implementation of the strategy only. prefer simple code.
- Do not use fully qualified annotation names. Use imports instead
- Prefer enums over plain Strings for finite, well-defined, values

## Package Structure Guidelines

- The top level package in ECB / BCE reflects the responsibility of the application or it is the application name
- Children of the top level ECB / BCE package are called business components and are named after their responsibilities
- the package structure is "com.github.bogdanpc". [project name]. [business component namel. [boundary| control|entity!


### Benefits of Business Packages
- **Cohesion**: Related functionality stays together
- **Encapsulation**: Internal implementation details can be package-private
- **Independence**: Changes to one domain are less likely to affect others
- **Clarity**: Package names reflect what the system does, not how it's built

### Package-Private Classes
Use package-private visibility for internal implementation classes that don't need to be exposed outside their domain package.