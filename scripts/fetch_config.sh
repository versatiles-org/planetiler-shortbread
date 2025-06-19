#!/bin/bash

set -e
cd $(dirname $0)/../resources/config

URL="https://github.com/onthegomap/planetiler/raw/refs/heads/main/planetiler-custommap/src/main/resources/samples"
curl -Ls "$URL/shortbread.yml" > shortbread.yml
curl -Ls "$URL/shortbread.spec.yml" > shortbread.spec.yml
