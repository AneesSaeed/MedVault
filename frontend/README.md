# Frontend - Secure Medical Records

Angular frontend application for managing encrypted medical records with Keycloak authentication.

## Features

- **Keycloak Authentication**: Single Sign-On (SSO) with WebAuthn support
- **Client-Side Encryption**: All medical data encrypted using Web Crypto API (AES-GCM, RSA-OAEP)
- **Role-Based Access**: Patient and Doctor roles with different permissions
- **Medical Records Management**: Upload, view, and share encrypted medical files
- **Patient-Doctor Relationships**: Secure sharing of medical data between patients and doctors
- **Centralized Logging**: Integration with ELK Stack (Logstash) for audit logs

## Development

### Prerequisites

- Node.js 20+
- npm or yarn

### Setup

```bash
npm install
```

### Development Server

Run `ng serve` for a dev server. Navigate to `https://localhost/`. The application will automatically reload if you change any of the source files.

**Note:** The application requires HTTPS and is served through Nginx reverse proxy in production.

### Build

Run `ng build` to build the project. The build artifacts will be stored in the `dist/` directory.

### Running Unit Tests

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

### Linting

Run `ng lint` to check code quality, or `ng lint --fix` to automatically fix issues.

## Architecture

- **Components**: Standalone Angular components
- **Services**: Core services for authentication, encryption, API calls
- **Models**: TypeScript interfaces for data models
- **Utils**: Utility functions (base64, sanitization)
- **Keycloak Integration**: Custom factory for Keycloak initialization

## Security

- All sensitive data encrypted client-side before transmission
- Private keys stored in browser localStorage (never sent to server)
- TLS/HTTPS for all communications
- Content Security Policy (CSP) headers
- Inactivity timeout for automatic logout

## Further Help

For more information about Angular CLI, use `ng help` or check the [Angular CLI Overview](https://angular.io/cli) page.
