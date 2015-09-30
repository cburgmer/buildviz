#!/bin/bash

NEW_VERSION=$1
OLD_VERSION=$(git tag | tail -1)

if [ -z "$NEW_VERSION" ]; then
    echo "Provide a new version number"
    exit 1
fi

sed -i "" "s/$OLD_VERSION/$NEW_VERSION/g" README.md
sed -i "" "s/buildviz \"$OLD_VERSION\"/buildviz \"$NEW_VERSION\"/" project.clj

git add README.md project.clj
git commit -m "Bump version"
git show
git tag $NEW_VERSION
