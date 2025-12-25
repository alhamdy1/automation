# Contributing to Pass Photo Processor

## Development Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Setup Steps
1. Clone the repository
2. Open project in Android Studio
3. Sync Gradle files
4. Build and run on device/emulator

## Code Style

### Kotlin Style Guide
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Use meaningful variable and function names

### Documentation
- Document all public classes and functions with KDoc
- Include `@param`, `@return`, and `@throws` where applicable
- Document security-critical code with extra detail

## Testing

### Unit Tests
- Location: `app/src/test/java/`
- Run with: `./gradlew test`
- Coverage target: >80% for security classes

### Instrumented Tests  
- Location: `app/src/androidTest/java/`
- Run with: `./gradlew connectedAndroidTest`
- Test on both physical devices and emulators

## Security Considerations

### Before Submitting PR
1. Ensure no hardcoded secrets or API keys
2. Run security linting: `./gradlew lint`
3. Review for potential vulnerabilities
4. Test tamper detection on both rooted and non-rooted devices

### Security Checklist
- [ ] No plaintext sensitive data in logs
- [ ] All network calls use HTTPS with certificate pinning
- [ ] Input validation on all user inputs
- [ ] Proper exception handling without leaking sensitive info
- [ ] ProGuard rules updated for new classes

## Pull Request Process

1. Create feature branch from `main`
2. Make changes with clear commits
3. Add tests for new functionality
4. Update documentation if needed
5. Submit PR with detailed description
6. Address review comments

## Reporting Security Issues

**DO NOT** create public GitHub issues for security vulnerabilities.

Please email security concerns to the maintainers privately.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
