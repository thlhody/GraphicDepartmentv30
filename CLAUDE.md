# CTGraphDep Web Application

A Spring Boot web application for time tracking, project management, and team coordination built with Java 17.

## Project Overview

- **Name**: CTGraphDep Web Application (CTTT)
- **Version**: 7.1.9
- **Framework**: Spring Boot 3.2.0
- **Java Version**: 17
- **Build Tool**: Maven

## Key Technologies

- **Backend**: Spring Boot, Spring Security, Spring Data JPA
- **Frontend**: Thymeleaf, HTML, CSS, JavaScript
- **Database**: H2 (in-memory)
- **Testing**: JUnit 5, Mockito
- **Office Integration**: Apache POI for Excel processing
- **Validation**: Jakarta Validation, Hibernate Validator

## Application Features

- User session tracking and time management
- Project registration and administration
- Team coordination and worktime tracking
- Excel export functionality
- Dual location synchronization (local and network paths)
- Health monitoring and notification system
- Backup management with multiple levels

## Development Commands

### Build and Run
```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package application
mvn package

# Run application
mvn spring-boot:run
```

### Application Access
- **URL**: http://localhost:8447
- **Default Login**: admin/admin
- **SSL**: Disabled for development

## Project Structure

```
src/main/
├── java/com/ctgraphdep/
│   ├── service/          # Business logic
│   ├── controller/       # Web controllers
│   ├── model/           # Data models
│   └── config/          # Configuration classes
├── resources/
│   ├── templates/       # Thymeleaf templates
│   ├── static/         # CSS, JS, images
│   └── application.properties
└── test/               # Unit and integration tests
```

## Configuration Highlights

- **Server Port**: 8447
- **Session Timeout**: 30 minutes
- **Development Mode**: Live reload enabled
- **Logging**: DEBUG level for com.ctgraphdep package
- **Backup Retention**: 30 days with tiered backup levels

## Key Paths

- **Local Home**: `D:\serverlocalhome`
- **Network Path**: `\\THLHODY-PC\servernetworktest\CTTT`
- **Logs**: `${app.home}/logs`
- **Database**: In-memory H2

## Recent Changes

Based on git history, recent work includes:
- Time management HTML extraction into fragments
- Session page notification updates
- Calculation command package removal
- User-friendly frontend JavaScript improvements
- Registry parameter additions

## Development Notes

- Template caching is disabled for development
- Static resources are served from file system for live editing
- DevTools restart is enabled for hot reloading
- Thymeleaf debug logging can be enabled when needed