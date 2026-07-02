import { readFileSync, writeFileSync } from 'fs';
import pc from 'picocolors';
import semver from 'semver';
import { PACKAGE_JSON_PATH, EXAMPLE_PACKAGE_JSON_PATH } from './constants';

export function getCurrentVersion(): string {
	const pkg = JSON.parse(readFileSync(PACKAGE_JSON_PATH, 'utf-8'));
	return pkg.version as string;
}

export function bumpPackageJson(
	pkgPath: string,
	currentVersion: string,
	newVersion: string,
	label: string
): void {
	const pkg = JSON.parse(readFileSync(pkgPath, 'utf-8'));
	pkg.version = newVersion;
	writeFileSync(pkgPath, JSON.stringify(pkg, null, 2) + '\n', 'utf-8');
	console.log(
		pc.green(
			`Bumped version in ${label}: ${currentVersion} → ${newVersion}`
		)
	);
}

export function bumpAllPackageFiles(
	currentVersion: string,
	newVersion: string
): void {
	bumpPackageJson(
		PACKAGE_JSON_PATH,
		currentVersion,
		newVersion,
		'package.json'
	);
	bumpPackageJson(
		EXAMPLE_PACKAGE_JSON_PATH,
		currentVersion,
		newVersion,
		'example/package.json'
	);
}

export function getNpmTag(version: string): string | undefined {
	const parsed = semver.parse(version);
	if (!parsed || parsed.prerelease.length === 0) return undefined;
	return parsed.prerelease[0] as string;
}
