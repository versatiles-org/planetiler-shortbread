#!/bin/bash

docker run \
   --rm \
   -m 0 \
   -v "$(pwd)/data:/app/data" \
   versatiles-planetiler:latest \
   $@
