# Contributing

Thanks for improving the INNOVV K5 SDK.

## Ground rules

- The public API is under `com.zoloft.innovv.sdk`. Internal helpers live under
  `.internal` and may change without notice.
- The project uses `explicitApi()`. Every public declaration needs explicit
  visibility and a return type.
- All public classes, functions, properties, and exceptions need KDoc.
- New protocol behavior must be backed by evidence. Add a captured response or
  a reference to the relevant `rea/` document/command.
- Do not add response-body logging. The stock app's `BODY` logging is a known
  performance problem.
- Do not retry write commands automatically.
- Keep the SDK platform-neutral. Android/iOS-specific networking belongs in the
  application, not in the common client.

## Development

```bash
./gradlew :innovv-sdk:jvmTest
./gradlew :innovv-sdk:compileAndroidMain
./gradlew :innovv-sdk:compileKotlinIosSimulatorArm64
```

On macOS, also link a framework:

```bash
./gradlew :innovv-sdk:linkDebugFrameworkIosSimulatorArm64
```

## Tests

- Add a `MockEngine` test for every new request shape.
- Add a parser test for every new response shape.
- Add a hardware note when a behavior can only be verified on a real K5.

## Commit style

Use a short imperative subject, for example:

```
Add event stream reconnect backoff
Document 4005 file-URL behavior
```

## License

By contributing, you agree that your contributions are licensed under the MIT
License.
