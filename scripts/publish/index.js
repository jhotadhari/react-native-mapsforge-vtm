#!/usr/bin/env node
/* eslint-env node */
/* global __filename */

const path = require('path');
const tsx = require('tsx/cjs/api');

const { publish } = tsx.require(
	path.resolve(__dirname, './publish.ts'),
	__filename
);

publish().catch((err) => {
	console.error(err?.message ?? err);
	process.exit(1);
});
