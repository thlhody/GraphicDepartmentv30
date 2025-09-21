# CTGraphDep Frontend Interface Documentation

## Overview
CTGraphDep is a Spring Boot web application with a Thymeleaf-based frontend for time tracking, project management, and team coordination.

## Frontend Architecture

### Static Resources (`src/main/resources/static/`)
```
static/
├── css/              # 30+ CSS files for styling
├── js/               # JavaScript files for interactivity
├── images/           # Application images
├── icons/            # UI icons
└── favicon.ico       # Site favicon
```

#### CSS Structure
- **Base Styles**: `normalize.css`, `variables.css`, `typography.css`, `layout.css`
- **Page-Specific**: Each major page has dedicated CSS (login.css, dashboard.css, session.css, etc.)
- **Component Styles**: Modular CSS for specific UI components (alerts.css, holiday-request-modal.css)

#### JavaScript Files
Core JS files include:
- `default.js` - Common functionality
- `login.js` - Authentication handling
- `dashboard.js` - Dashboard interactions
- `session.js` - Session management
- Page-specific scripts for admin, user, and checking modules

### Templates (`src/main/resources/templates/`)
```
templates/
├── layout/           # Common layout fragments
├── admin/            # Administrator pages
├── user/             # User-specific pages
├── dashboard/        # Role-based dashboards
├── alerts/           # Notification components
├── logs/             # Logging interfaces
├── status/           # Status monitoring
├── utility/          # Utility pages
├── login.html        # Authentication page
├── about.html        # Application information
└── utility.html      # General utilities
```

#### Key Template Categories

**Dashboard Templates** (Role-based):
- Admin dashboard
- User dashboard
- Team lead dashboard
- Checking dashboard
- Team checking dashboard
- User checking dashboard

**Admin Templates**:
- User registration and management
- Holiday management
- Statistics and reporting
- Bonus administration
- Settings configuration
- Work time tracking

**User Templates**:
- Personal session management
- Time tracking interface
- Settings and preferences
- Holiday requests

## Technology Stack

### Frontend Technologies
- **Template Engine**: Thymeleaf
- **Styling**: CSS3 with custom variables and modular structure
- **Scripting**: Vanilla JavaScript (no major frameworks)
- **UI Components**: Custom-built components
- **Icons**: Custom icon set
- **Responsive Design**: CSS-based responsive layouts

### Integration Points
- **Server Communication**: Form submissions and AJAX calls to Spring Boot controllers
- **Session Management**: Server-side sessions with Thymeleaf context
- **Authentication**: Spring Security integration
- **Data Binding**: Thymeleaf model binding with Spring MVC

## Key Features

### User Interface Components
- **Login/Authentication**: Secure login with session management
- **Dashboard System**: Role-based dashboards with different permissions
- **Time Tracking**: Interactive time entry and session management
- **Admin Panel**: Comprehensive administration interface
- **Notifications**: Toast alerts and notification system
- **Modal Dialogs**: Holiday requests, confirmations, and data entry
- **Data Tables**: Sortable and searchable data presentation
- **Export Functions**: Excel export capabilities

### Responsive Design
- Mobile-friendly layouts
- Flexible grid system
- Adaptive navigation
- Touch-friendly controls

## Separation Strategy for Standalone Frontend

### API Endpoints to Identify
To separate this frontend into a standalone project, you would need to:

1. **Map Current Form Actions**: Extract all form `action` attributes and AJAX endpoints
2. **Identify Data Models**: Document the data structures passed from controllers to templates
3. **Create REST API**: Convert current Spring MVC endpoints to RESTful JSON APIs
4. **Replace Thymeleaf**: Convert templates to static HTML with JavaScript for dynamic content
5. **Implement Client-Side Routing**: Replace server-side navigation with client-side routing
6. **Add State Management**: Implement client-side state management for session data

### Recommended Approach
- **Static Site Generator**: Convert Thymeleaf templates to static HTML
- **Frontend Framework**: Consider React/Vue.js for complex interactions
- **API Client**: Create TypeScript/JavaScript API client
- **Build System**: Use Webpack/Vite for asset bundling
- **Development Server**: Set up development server with API proxy

### Current Dependencies
The frontend currently depends on:
- Spring Boot for serving static resources
- Thymeleaf for server-side rendering
- Spring Security for authentication context
- Spring MVC for form handling and data binding
- Server-side session management

---

**Note**: This interface represents a traditional server-rendered web application. Converting to a modern SPA would require significant architectural changes to both frontend and backend components.