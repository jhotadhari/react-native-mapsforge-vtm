import { execSync } from 'child_process';
import pc from 'picocolors';
import { Octokit } from '@octokit/rest';
import { ROOT } from './constants';
import { fatalError } from './checks';
import { extractReleaseBody } from './changelog';

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
		return { owner: sshMatch[1]!, repo: sshMatch[2]! };
	}

	// ssh://git@github.com/owner/repo.git (standard SSH protocol)
	const sshProtocolMatch = remoteUrl.match(
		/ssh:\/\/git@github\.com\/([^/]+)\/(.+?)(?:\.git)?$/
	);
	if (sshProtocolMatch) {
		return {
			owner: sshProtocolMatch[1]!,
			repo: sshProtocolMatch[2]!.replace(/\.git$/, ''),
		};
	}

	// https://github.com/owner/repo.git
	const httpsMatch = remoteUrl.match(
		/https?:\/\/github\.com\/([^/]+)\/(.+?)(?:\.git)?$/
	);
	if (httpsMatch) {
		return {
			owner: httpsMatch[1]!,
			repo: httpsMatch[2]!.replace(/\.git$/, ''),
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

export async function createGitHubRelease(version: string): Promise<void> {
	const token = process.env.GITHUB_TOKEN;
	if (!token) {
		fatalError(
			'GITHUB_TOKEN environment variable is required to create a GitHub release. ' +
				'Run `export GITHUB_TOKEN=$(gh auth token)` or generate one at ' +
				'https://github.com/settings/tokens (scope: repo).'
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
