# MyPassword

Open source desktop password manager.

## How to build

### Desktop app (Maven)

Run from the `desktop/` directory:

```bash
cd desktop
mvn package
```

The active `jpackage` profile (`win64`, `macos-arm64`, `linux64`) is selected automatically from the host OS and produces a native installer / app image under `desktop/target/`.

### Chrome extension (zip)

Package the unpacked extension into a zip for distribution / upload:

```bash
cd extension/chrome
zip -r ../mypassword-chrome.zip . -x "*.DS_Store"
```

The resulting `extension/mypassword-chrome.zip` can be loaded into Chrome via *Load unpacked* (after unzipping) or uploaded to the Chrome Web Store.
