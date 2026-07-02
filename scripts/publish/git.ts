import pc from 'picocolors';
import { git } from './constants';

export async function getCurrentBranch(): Promise<string> {
	return (await git.status()).current ?? '';
}

export async function gitStageAllAndCommit(message: string): Promise<void> {
	await git.add('.');
	await git.commit(message);
	console.log(pc.green(`Committed: ${message}`));
}

export async function gitMergeToMain(releaseBranch: string): Promise<void> {
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

export async function gitTagAndPush(version: string): Promise<void> {
	const tagName = `v${version}`;
	await git.addTag(tagName);
	// Push tag first so a network failure between pushes leaves a tag
	// without the branch commit rather than the branch without the tag.
	await git.push(['origin', tagName]);
	await git.push();
	console.log(pc.green(`Pushed tag ${tagName}`));
}

export async function gitMergeToDevelopment(
	releaseBranch: string
): Promise<void> {
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

export async function gitStageChangelogAndCommit(
	message: string
): Promise<void> {
	await git.add('CHANGELOG.md');
	await git.commit(message);
	console.log(pc.green(`Committed: ${message}`));
}

export async function gitPush(): Promise<void> {
	await git.push();
	console.log(pc.green('Pushed to origin'));
}
