import { readFileSync, writeFileSync } from 'fs';
import pc from 'picocolors';
import { parser, Release } from 'keep-a-changelog';
import type { Changelog } from 'keep-a-changelog';
import { CHANGELOG_PATH, GITHUB_REPO_URL } from './constants';
import { fatalError } from './checks';

export function readChangelog(): Changelog {
	const content = readFileSync(CHANGELOG_PATH, 'utf-8');
	return parser(content, { autoSortReleases: true });
}

export function writeChangelog(changelog: Changelog): void {
	changelog.url = GITHUB_REPO_URL;
	changelog.format = 'markdownlint';
	writeFileSync(CHANGELOG_PATH, changelog.toString(), 'utf-8');
}

export function releaseChangelog(version: string): void {
	const changelog = readChangelog();

	const unreleased = changelog.releases.find((r) => !r.version);
	if (!unreleased) {
		fatalError('No unreleased section found in CHANGELOG.md');
	}

	unreleased.setVersion(version);
	unreleased.setDate(new Date());

	writeChangelog(changelog);
	console.log(pc.green(`Released changelog: [Unreleased] → [${version}]`));
}

export function addUnreleasedSection(): void {
	const changelog = readChangelog();
	changelog.addRelease(new Release());
	writeChangelog(changelog);
	console.log(pc.green('Added [Unreleased] section to CHANGELOG.md'));
}

export function extractReleaseBody(version: string): string {
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
