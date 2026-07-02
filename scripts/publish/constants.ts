import path from 'path';
import { fileURLToPath } from 'url';
import simpleGit from 'simple-git';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const ROOT = path.resolve(__dirname, '..', '..');

export const CHANGELOG_PATH = path.resolve(ROOT, 'CHANGELOG.md');
export const PACKAGE_JSON_PATH = path.resolve(ROOT, 'package.json');
export const EXAMPLE_PACKAGE_JSON_PATH = path.resolve(
	ROOT,
	'example',
	'package.json'
);

export const GITHUB_REPO_URL =
	'https://github.com/jhotadhari/react-native-mapsforge-vtm';

export const git = simpleGit(ROOT);
