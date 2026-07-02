import { execSync } from 'child_process';
import { readFileSync } from 'fs';
import pc from 'picocolors';
import semver from 'semver';
import { CHANGELOG_PATH, PACKAGE_JSON_PATH, ROOT, git } from './constants';

export function fatalError(message: string): never {
	console.error(pc.red('ERROR'), message);
	process.exit(1);
}

function getCurrentVersion(): string {
	const pkg = JSON.parse(readFileSync(PACKAGE_JSON_PATH, 'utf-8'));
	return pkg.version as string;
}

export async function validateVersionIsHigher(
	newVersion: string
): Promise<void> {
	await git.fetch(['--tags']);
	const currentVersion = getCurrentVersion();

	if (semver.lte(newVersion, currentVersion)) {
		fatalError(
			`New version (${newVersion}) must be higher than current version (${currentVersion})`
		);
	}
}

export async function checkCleanWorkingTree(): Promise<void> {
	const status = await git.status();
	if (!status.isClean()) {
		const dirtyFiles = status.files
			.map((f) => `  ${f.index} ${f.path}`)
			.join('\n');
		fatalError('Unable to publish. Uncommitted changes.\n' + dirtyFiles);
	}
}

export function checkChangelogHasUnreleased(): void {
	const content = readFileSync(CHANGELOG_PATH, 'utf-8');
	if (!/## \[?Unreleased\]?/.test(content)) {
		fatalError('CHANGELOG.md should have a `[Unreleased]` section');
	}
}

export async function checkBranchIsRelease(): Promise<void> {
	const branch = (await git.branch()).current;
	if (!branch.startsWith('release')) {
		fatalError('Current branch name should start with `release`');
	}
}

export function runTypeCheck(): void {
	console.log(pc.blue('Running typecheck…'));
	try {
		execSync('yarn run typecheck', { stdio: 'inherit', cwd: ROOT });
	} catch {
		fatalError(
			'Unable to publish. TypeScript compile complains errors. Fix them first!'
		);
	}
}

export function runLint(skip: boolean): void {
	if (skip) {
		console.log(pc.yellow('Skipping lint (--no-lint)'));
		return;
	}
	console.log(pc.blue('Running lint…'));
	try {
		execSync('yarn lint', { stdio: 'inherit', cwd: ROOT });
	} catch {
		fatalError('Unable to publish. Lint errors found. Fix them first!');
	}
}

export function runTests(skip: boolean): void {
	if (skip) {
		console.log(pc.yellow('Skipping tests (--no-test)'));
		return;
	}
	console.log(pc.blue('Running tests…'));
	try {
		execSync('yarn test', { stdio: 'inherit', cwd: ROOT });
	} catch {
		fatalError('Unable to publish. Tests failed. Fix them first!');
	}
}
