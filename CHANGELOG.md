# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/)
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]
Just updated README.md

## [0.1.2] - 2024-10-30
### Fixed
- Layer props: make reactTreeIndex optional. It is required but the MapContainer will mix it in

## [0.1.1] - 2024-10-30
### Fixed
- Publish script changelog

## [0.1.0] - 2024-10-30
### Added
- Better example app.
- Catch blocks to all native module methods. And `onError` function prop to all components and hooks.
- New `MapContainer` prop: `responseInclude`. We don't need to send everything always through the bridge bottleneck.
- New `MapContainer` prop: `mapEventRate`, the update rate of map events.
- `MapContainerModule` method: `setToBounds`.
- Some components respond their `bounds` and more meta data.
- New `LayerPath` prop: `style`. To parse almost all options from js to java `org.oscim.layers.vector.geometries.Style` (`texture` not supported yet).

### Changed
- Migrated all js to typeScript.
- Huge refactor.
- Some variable names and props have changed.

## [0.0.1] - 2024-10-25
First bumpy version

[Unreleased]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.2...HEAD
[0.1.2]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/jhotadhari/react-native-mapsforge-vtm/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/jhotadhari/react-native-mapsforge-vtm/releases/tag/v0.0.1
