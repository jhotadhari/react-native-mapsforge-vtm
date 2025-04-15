#!/usr/bin/env bash

# Setup OS specific stuff
case "$OS" in
	mac)
		grep=ggrep
		sed=gsed
	;;
	*)
		grep=grep
		sed=sed
	;;
esac

# Text formatting
T_RED=$(tput setaf 1)
T_RESET=$(tput sgr0)

# Check OS specific stuff
if [ -z $( which $grep ) ]; then
	echo "${T_RED}ERROR${T_RESET} command not found: $grep"
	if [ $OS = mac ]; then
		echo "You are on mac and need to install GNU Grep"
		echo "Try: brew install grep"
	fi
	exit 1
fi

if [ -z $( which $sed ) ]; then
	echo "${T_RED}ERROR${T_RESET} command not found: $sed"
	if [ $OS = mac ]; then
		echo "You are on mac and need to install GNU sed"
		echo "Try: brew install gnu-sed"
	fi
	exit 1
fi

# Check if gh cli is installed.
if [[ -z $( gh --version ) ]]; then
    echo "${T_RED}ERROR${T_RESET} GitHub CLI should be installed. See https://cli.github.com"
    exit 1
fi

# Check if CHANGELOG.md is valid.
if [[ -z $( ./node_modules/.bin/changelog ) ]]; then
    echo "${T_RED}ERROR${T_RESET} Unable to parse \`CHANGELOG.md\`"
    exit 1
fi

# CHANGELOG.md should have a Unreleased section.
if [[ -z $( $grep -P '## \[?Unreleased\]?' CHANGELOG.md ) ]]; then
    echo "${T_RED}ERROR${T_RESET} \`CHANGELOG.md\` should have a \`[Unreleased]\` section"
    exit 1
fi

# typeScript compile should not complain.
yarn run tsc
if ! [[ $? == 0 ]]; then
    echo "${T_RED}ERROR${T_RESET} Unable to publish. TypeScript compile complains errors. Fix them first!"
    exit 1
fi

# git status should be clean.
if [[ ! -z $( git status --short ) ]]; then
    echo "${T_RED}ERROR${T_RESET} Unable to publish. Uncommitted changes."
    git status --short
    exit 1
fi

# version should be specified as first arg.
if [[ -z $1 ]]; then
    echo "${T_RED}ERROR${T_RESET} No version specified. Run \`yarn run release <version>\` (Just the version number. Without prepending \`v\`)"
    exit 1
fi

# version should be semver.
next_version=$1
pat="^[0-9]+\.[0-9]+\.[0-9]+$"
if ! [[ "$next_version" =~ $pat ]]; then
    echo "${T_RED}ERROR${T_RESET} Version should be SemVer. Run \`yarn run release <major>.<minor>.<patch>\`"
    exit 1
fi

# new version should be higher then current version.
git fetch --tags
if ! [[ $? == 0 ]]; then
    echo "${T_RED}ERROR${T_RESET} git fetch tags failed. Command was \`git fetch --tags\`"
    exit 1
fi
newest_version=$( git tag -l --sort=-version:refname 'v*' | head -n 1 | $sed -r 's/v//g' )
if ! [[ -z newest_version ]]; then
    newest_version=$( node -p "require('./package.json').version" )
fi
verlte() {
    [  "$1" = "`echo -e "$1\n$2" | sort -V | head -n1`" ]
}
verlt() {
    [ "$1" = "$2" ] && return 1 || verlte $1 $2

}
if ! [ -z $( verlt $newest_version $next_version || echo "1") ]; then
    echo "${T_RED}ERROR${T_RESET} New version should be higher then current version ${newest_version}"
    exit 1
fi

# current branch should start with release*.
release_branch=$( git rev-parse --abbrev-ref HEAD )
if ! [[ $release_branch == release* ]]; then
    echo "${T_RED}ERROR${T_RESET} Current branch name should start with \`release\`"
    exit 1
fi

# bump package version, update CHANGELOG.md and commit changes.
current_version=$( node -p "require('./package.json').version" )
$sed -i "0,/\"version\": \"${current_version}\"/{s/\"version\": \"${current_version}\"/\"version\": \"${next_version}\"/}" package.json
$sed -i "0,/\"version\": \"${current_version}\"/{s/\"version\": \"${current_version}\"/\"version\": \"${next_version}\"/}" example/package.json
./node_modules/.bin/changelog --release "${next_version}"
git add .
git commit -m "Bump version ${next_version}"

# git checkout main
git checkout main
if ! [[ $? == 0 ]]; then
    echo "${T_RED}ERROR${T_RESET} git checkout failed. Command was \`git checkout main\`"
    exit 1
fi

# git merge release_branch
git merge $release_branch --no-ff --commit --no-edit
if ! [[ $? == 0 ]]; then
    echo "${T_RED}ERROR${T_RESET} git merge into main failed. Command was \`git merge $release_branch --no-ff --commit --no-edit\`"
    exit 1
fi

# git tag
git tag "v${next_version}"

# git push
git push
git push origin "v${next_version}"
if ! [[ $? == 0 ]]; then
    echo "${T_RED}ERROR${T_RESET} git push failed. Command was \`git push && git push origin \"v${next_version}\"\`"
    exit 1
fi

# add release description from changelog and publish the release.
line_from=$(( $( awk "/## \[${next_version}\]/{ print NR; exit }" CHANGELOG.md ) + 1 ))
line_to=$( awk "/## \[${newest_version}\]/{ print NR; exit }" CHANGELOG.md )
if [[ -z $line_to ]]; then
    line_to=$( awk "/...HEAD/{ print NR; exit }" CHANGELOG.md )
fi
if [[ -z $line_to ]]; then
    line_to=$(( $( wc -l < CHANGELOG.md ) + 1 ))
fi
line_to=$(( $line_to - 1 ))
if [[ -z $( gh release list | $grep "v${next_version}" ) ]]; then
    gh_command='create'
else
    gh_command='edit'
fi
$sed -n ${line_from},${line_to}p CHANGELOG.md | gh release "${gh_command}" "v${next_version}" --draft=false -F -
if ! [[ $? == 0 ]]; then
    echo "${T_RED}ERROR${T_RESET} Failed to add github release. Command was \`$sed -n ${line_from},${line_to}p CHANGELOG.md | gh release \"${gh_command}\" \"v${next_version}\" --draft=false -F -\`"
    exit 1
fi

# publish to npm
npm publish

# checkout development, merge release-branch into development
git checkout development
git merge $release_branch --no-ff --commit --no-edit
if ! [[ $? == 0 ]]; then
    echo "${T_RED}ERROR${T_RESET} git merge into development failed. Command was \"git merge $release_branch --no-ff --commit --no-edit\""
    exit 1
fi

# Add [Unreleased] section to CHANGELOG.md and push.
line=$( awk "/## \[${next_version}\]/{ print NR; exit }" CHANGELOG.md )
awk -i inplace "NR==${line}{print \"## [Unreleased]\n\"}1" CHANGELOG.md
./node_modules/.bin/changelog
git add CHANGELOG.md
git commit -m "Add [Unreleased] section to CHANGELOG.md"
git push
