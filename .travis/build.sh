#!/bin/bash

# Copyright 2015 Google Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -eux

if [ -z "${TRAVIS_OS_NAME+x}" ]; then
    echo "TRAVIS_OS_NAME not set, set it to 'linux' or 'osx' to run locally."
    exit 1
fi

if [[ $TRAVIS_OS_NAME = 'osx' ]]; then
    brew install protobuf libarchive hg
    hg clone http://hg.openjdk.java.net/jdk8/jdk8
    cd jdk8
    chmod 755 get_source.sh
    ./get_source.sh
    ./configure
    make
    ls
else
    sudo apt-get update -qq
    sudo apt-get install -y protobuf-compiler libarchive-dev netcat-traditional
    sudo update-alternatives --set nc /bin/nc.traditional
    export JAVA_HOME=/usr/lib/jvm/java-8-oracle
    export JAVA_OPTS="-Xmx3000m"
    ./bootstrap_test.sh all
fi
