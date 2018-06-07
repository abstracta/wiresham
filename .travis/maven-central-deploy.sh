#!/bin/bash
#
# This script takes care of deploying tagged versions to maven central and updating the pom.xml
# version with next development version.
#
# Required environment variables: TRAVIS_TAG, GPG_SECRET_KEYS, GPG_OWNERTRUST, GPG_EXECUTABLE

set -eo pipefail

echo $GPG_SECRET_KEYS | base64 --decode | $GPG_EXECUTABLE --import
echo $GPG_OWNERTRUST | base64 --decode | $GPG_EXECUTABLE --import-ownertrust
mvn --batch-mode deploy -Prelease -DskipTests --settings .travis/settings.xml
