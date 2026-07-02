import pc from 'picocolors';
import { parseArgs } from './cli';
import {
	validateVersionIsHigher,
	checkCleanWorkingTree,
	checkChangelogHasUnreleased,
	checkBranchIsRelease,
	runTypeCheck,
	runLint,
	runTests,
} from './checks';
import { getCurrentVersion, bumpAllPackageFiles } from './version';
import { releaseChangelog, addUnreleasedSection } from './changelog';
import {
	getCurrentBranch,
	gitStageAllAndCommit,
	gitMergeToMain,
	gitTagAndPush,
	gitMergeToDevelopment,
	gitStageChangelogAndCommit,
	gitPush,
} from './git';
import { createGitHubRelease } from './github';
import { runBuild, npmPublish } from './npm';

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

	// 4. Bump version in package files
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
		'chore: add [Unreleased] section to CHANGELOG.md'
	);
	await gitPush();

	console.log(pc.green(`Done — v${version} published successfully.`));
};
