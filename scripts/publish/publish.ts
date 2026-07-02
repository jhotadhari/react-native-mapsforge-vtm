import { execSync } from 'child_process';
import { readFileSync, writeFileSync } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

import pc from 'picocolors';
import semver from 'semver';
import simpleGit from 'simple-git';
import { Octokit } from '@octokit/rest';
import { Command } from 'commander';
import { parser, Release } from 'keep-a-changelog';
import type { Changelog } from 'keep-a-changelog';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..', '..');

const CHANGELOG_PATH = path.resolve(ROOT, 'CHANGELOG.md');
const PACKAGE_JSON_PATH = path.resolve(ROOT, 'package.json');
const EXAMPLE_PACKAGE_JSON_PATH = path.resolve(ROOT, 'example', 'package.json');

const GITHUB_REPO_URL =
	'https://github.com/jhotadhari/react-native-mapsforge-vtm';

const git = simpleGit(ROOT);

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

function fatalError(message: string): never {
	console.error(pc.red('ERROR'), message);
	process.exit(1);
}

function getCurrentVersion(): string {
	const pkg = JSON.parse(readFileSync(PACKAGE_JSON_PATH, 'utf-8'));
	return pkg.version as string;
}

// ---------------------------------------------------------------------------
// Argument parsing / version checks
// ---------------------------------------------------------------------------

function parseArgs(): {
	version: string;
	dryRun: boolean;
	test: boolean;
	lint: boolean;
} {
	const program = new Command();
	program
		.name('yarn release')
		.description('Publish a new release of the library')
		.argument('<version>', 'SemVer version (e.g., 1.0.0, 1.0.0-alpha.1)')
		.option('--dry-run', 'Validate only, skip all mutations')
		.option('--no-test', 'Skip tests pre-flight')
		.option('--no-lint', 'Skip lint pre-flight')
		.allowExcessArguments(false)
		.parse();

	const version = program.args[0]!;
	if (!semver.valid(version)) {
		fatalError(
			'Version should be valid SemVer. ' +
				'Run `yarn release <major>.<minor>.<patch>` ' +
				'(pre-release tags like alpha/beta/rc are supported, e.g. `0.8.0-alpha.1`)'
		);
	}

	const opts = program.opts();
	return {
		version,
		dryRun: opts.dryRun === true,
		test: opts.test !== false,
		lint: opts.lint !== false,
	};
}

async function validateVersionIsHigher(newVersion: string): Promise<void> {
	await git.fetch(['--tags']);
	const currentVersion = getCurrentVersion();

	if (semver.lte(newVersion, currentVersion)) {
		fatalError(
			`New version (${newVersion}) must be higher than current version (${currentVersion})`
		);
	}
}

// ---------------------------------------------------------------------------
// Pre-flight checks
// ---------------------------------------------------------------------------

async function checkCleanWorkingTree(): Promise<void> {
	const status = await git.status();
	if (!status.isClean()) {
		const dirtyFiles = status.files
			.map((f) => `  ${f.index} ${f.path}`)
			.join('\n');
		fatalError('Unable to publish. Uncommitted changes.\n' + dirtyFiles);
	}
}

function checkChangelogHasUnreleased(): void {
	const content = readFileSync(CHANGELOG_PATH, 'utf-8');
	if (!/## \[?Unreleased\]?/.test(content)) {
		fatalError('CHANGELOG.md should have a `[Unreleased]` section');
	}
}

async function checkBranchIsRelease(): Promise<void> {
	const branch = (await git.branch()).current;
	if (!branch.startsWith('release')) {
		fatalError('Current branch name should start with `release`');
	}
}

function runTypeCheck(): void {
	console.log(pc.blue('Running typecheck…'));
	try {
		execSync('yarn run typecheck', { stdio: 'inherit', cwd: ROOT });
	} catch {
		fatalError(
			'Unable to publish. TypeScript compile complains errors. Fix them first!'
		);
	}
}

function runLint(skip: boolean): void {
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

function runTests(skip: boolean): void {
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

// ---------------------------------------------------------------------------
// Changelog helpers
// ---------------------------------------------------------------------------

function readChangelog(): Changelog {
	const content = readFileSync(CHANGELOG_PATH, 'utf-8');
	return parser(content, { autoSortReleases: true });
}

function writeChangelog(changelog: Changelog): void {
	changelog.url = GITHUB_REPO_URL;
	changelog.format = 'markdownlint';
	writeFileSync(CHANGELOG_PATH, changelog.toString(), 'utf-8');
}

function releaseChangelog(version: string): void {
	const changelog = readChangelog();

	// Find the unreleased entry (no version set)
	const unreleased = changelog.releases.find((r) => !r.version);
	if (!unreleased) {
		fatalError('No unreleased section found in CHANGELOG.md');
	}

	unreleased.setVersion(version);
	unreleased.setDate(new Date());

	writeChangelog(changelog);
	console.log(pc.green(`Released changelog: [Unreleased] → [${version}]`));
}

function addUnreleasedSection(): void {
	const changelog = readChangelog();
	changelog.addRelease(new Release());
	writeChangelog(changelog);
	console.log(pc.green('Added [Unreleased] section to CHANGELOG.md'));
}

function extractReleaseBody(version: string): string {
	const changelog = readChangelog();
	const release = changelog.findRelease(version);
	if (!release) {
		fatalError(`Cannot find release ${version} in CHANGELOG.md`);
	}

	const parts: string[] = [];

	if (release.description?.trim()) {
		parts.push(release.description.trim());
		parts.push('');
	}

	release.changes.forEach((changes, type) => {
		if (changes.length === 0) return;
		parts.push(`### ${type.charAt(0).toUpperCase() + type.slice(1)}`);
		changes.forEach((change) => {
			parts.push(change.toString());
		});
		parts.push('');
	});

	return parts.join('\n').trim();
}

// ---------------------------------------------------------------------------
// File bumping
// ---------------------------------------------------------------------------

function bumpPackageJson(
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

function bumpAllPackageFiles(currentVersion: string, newVersion: string): void {
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

// ---------------------------------------------------------------------------
// Git operations
// ---------------------------------------------------------------------------

async function getCurrentBranch(): Promise<string> {
	return (await git.status()).current ?? '';
}

async function gitStageAllAndCommit(message: string): Promise<void> {
	await git.add('.');
	await git.commit(message);
	console.log(pc.green(`Committed: ${message}`));
}

async function gitMergeToMain(releaseBranch: string): Promise<void> {
	console.log(pc.blue('Checking out main…'));
	await git.checkout('main');
	console.log(pc.blue(`Merging ${releaseBranch} into main…`));
	await git.merge([
		releaseBranch,
		'--no-ff',
		'--commit',
		'--no-edit',
	]);
	console.log(pc.green(`Merged ${releaseBranch} into main`));
}

async function gitTagAndPush(version: string): Promise<void> {
	const tagName = `v${version}`;
	await git.addTag(tagName);
	// Push tag first so a network failure between pushes leaves a tag
	// without the branch commit rather than the branch without the tag.
	await git.push(['origin', tagName]);
	await git.push();
	console.log(pc.green(`Pushed tag ${tagName}`));
}

async function gitMergeToDevelopment(releaseBranch: string): Promise<void> {
	console.log(pc.blue('Checking out development…'));
	await git.checkout('development');
	console.log(pc.blue(`Merging ${releaseBranch} into development…`));
	await git.merge([
		releaseBranch,
		'--no-ff',
		'--commit',
		'--no-edit',
	]);
	console.log(pc.green(`Merged ${releaseBranch} into development`));
}

async function gitStageChangelogAndCommit(message: string): Promise<void> {
	await git.add('CHANGELOG.md');
	await git.commit(message);
	console.log(pc.green(`Committed: ${message}`));
}

async function gitPush(): Promise<void> {
	await git.push();
	console.log(pc.green('Pushed to origin'));
}

// ---------------------------------------------------------------------------
// GitHub release
// ---------------------------------------------------------------------------

interface RepoInfo {
	owner: string;
	repo: string;
}

function getRepoInfo(): RepoInfo {
	const remoteUrl = execSync('git remote get-url origin', {
		encoding: 'utf-8',
		cwd: ROOT,
	}).trim();

	// git@github.com:owner/repo.git (scp-style SSH)
	const sshMatch = remoteUrl.match(/git@github\.com:([^/]+)\/(.+?)\.git$/);
	if (sshMatch) {
		return { owner: sshMatch[1], repo: sshMatch[2] };
	}

	// ssh://git@github.com/owner/repo.git (standard SSH protocol)
	const sshProtocolMatch = remoteUrl.match(
		/ssh:\/\/git@github\.com\/([^/]+)\/(.+?)(?:\.git)?$/
	);
	if (sshProtocolMatch) {
		return {
			owner: sshProtocolMatch[1],
			repo: sshProtocolMatch[2].replace(/\.git$/, ''),
		};
	}

	// https://github.com/owner/repo.git
	const httpsMatch = remoteUrl.match(
		/https?:\/\/github\.com\/([^/]+)\/(.+?)(?:\.git)?$/
	);
	if (httpsMatch) {
		return {
			owner: httpsMatch[1],
			repo: httpsMatch[2].replace(/\.git$/, ''),
		};
	}

	fatalError('Could not parse GitHub owner/repo from git remote origin');
}

async function findReleaseByTag(
	octokit: Octokit,
	owner: string,
	repo: string,
	tag: string
): Promise<{ id: number } | null> {
	try {
		const { data } = await octokit.rest.repos.getReleaseByTag({
			owner,
			repo,
			tag,
		});
		return { id: data.id };
	} catch (err: unknown) {
		if (
			err &&
			typeof err === 'object' &&
			'status' in err &&
			(err as { status: number }).status === 404
		) {
			return null;
		}
		throw err;
	}
}

async function createGitHubRelease(version: string): Promise<void> {
	const token = process.env.GITHUB_TOKEN;
	if (!token) {
		fatalError(
			'GITHUB_TOKEN environment variable is required to create a GitHub release. ' +
				'Generate one at https://github.com/settings/tokens (scope: repo).\n' +
				'Alternatively, run: export GITHUB_TOKEN=$(gh auth token)'
		);
	}

	const octokit = new Octokit({ auth: token });
	const { owner, repo } = getRepoInfo();
	const tagName = `v${version}`;
	const body = extractReleaseBody(version);

	const existingRelease = await findReleaseByTag(
		octokit,
		owner,
		repo,
		tagName
	);

	if (existingRelease) {
		await octokit.rest.repos.updateRelease({
			owner,
			repo,
			release_id: existingRelease.id,
			tag_name: tagName,
			name: tagName,
			body,
			draft: false,
		});
		console.log(pc.green(`Updated GitHub release: ${tagName}`));
	} else {
		await octokit.rest.repos.createRelease({
			owner,
			repo,
			tag_name: tagName,
			name: tagName,
			body,
			draft: false,
		});
		console.log(pc.green(`Created GitHub release: ${tagName}`));
	}
}

// ---------------------------------------------------------------------------
// Build
// ---------------------------------------------------------------------------

function runBuild(): void {
	console.log(pc.blue('Building library (yarn prepare)…'));
	try {
		execSync('yarn prepare', { stdio: 'inherit', cwd: ROOT });
	} catch {
		fatalError('Build failed. Fix build errors before publishing.');
	}
	console.log(pc.green('Build completed'));
}

// ---------------------------------------------------------------------------
// npm publish
// ---------------------------------------------------------------------------

function getNpmTag(version: string): string | undefined {
	const parsed = semver.parse(version);
	if (!parsed || parsed.prerelease.length === 0) return undefined;
	return parsed.prerelease[0] as string;
}

function npmPublish(version: string, dryRun: boolean): void {
	const tag = getNpmTag(version);
	const args: string[] = ['publish'];
	if (tag) args.push('--tag', tag);
	if (dryRun) args.push('--dry-run');
	const label = dryRun ? 'Dry-running npm publish' : 'Publishing to npm';
	const tagLabel = tag ? ` (tag: ${tag})` : '';
	console.log(pc.blue(`${label}${tagLabel}…`));
	try {
		execSync('npm ' + args.join(' '), { stdio: 'inherit', cwd: ROOT });
	} catch {
		fatalError(
			'npm publish failed. ' +
				'Make sure you are logged in (`npm whoami`) and have permission to publish this package.'
		);
	}
	if (dryRun) {
		console.log(pc.green('npm publish dry-run completed'));
	} else {
		console.log(
			pc.green(
				`Published ${version} to npm${tag ? ` (tag: ${tag})` : ''}`
			)
		);
	}
}

// ---------------------------------------------------------------------------
// Main orchestrator
// ---------------------------------------------------------------------------

export const publish = async (): Promise<void> => {
	// 1. Parse version and flags from CLI args
	const { version, dryRun, test, lint } = parseArgs();

	if (dryRun) {
		console.log(pc.yellow('--dry-run: no mutations will be made'));
	}
	console.log(
		pc.blue(`Publishing v${version}${dryRun ? ' (dry-run)' : ''}…`)
	);

	// 2. Pre-flight validations
	await validateVersionIsHigher(version);
	await checkCleanWorkingTree();
	checkChangelogHasUnreleased();
	await checkBranchIsRelease();
	runTypeCheck();
	runLint(!lint);
	runTests(!test);

	// 3. Dry-run: stop here, before any mutations
	if (dryRun) {
		console.log(
			pc.green(
				'All pre-flight checks passed. Ready to publish v' +
					version +
					'.'
			)
		);
		console.log(pc.yellow('Run again without --dry-run to publish.'));
		return;
	}

	// 4. Bump versions in package files
	const currentVersion = getCurrentVersion();
	bumpAllPackageFiles(currentVersion, version);

	// 5. Release changelog
	releaseChangelog(version);

	// 6. Stage & commit on release branch
	await gitStageAllAndCommit(`chore: release v${version}`);

	// 7. Merge to main, tag, push
	const releaseBranch = await getCurrentBranch();
	await gitMergeToMain(releaseBranch);
	await gitTagAndPush(version);

	// 8. GitHub release
	await createGitHubRelease(version);

	// 9. Build library
	runBuild();

	// 10. npm publish
	npmPublish(version, false);

	// 11. Merge to development
	await gitMergeToDevelopment(releaseBranch);

	// 12. Re-add [Unreleased] section, commit, push
	addUnreleasedSection();
	await gitStageChangelogAndCommit(
		'Add [Unreleased] section to CHANGELOG.md'
	);
	await gitPush();

	console.log(pc.green(`Done — v${version} published successfully.`));
};
