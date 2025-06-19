#!/bin/bash

# get the available memory
available_memory=$(free -m | awk '/^Mem:/{print $7}')

if [ "$available_memory" -lt 4096 ]; then
	# reserve 25% of the available memory
	available_memory=$(awk '{print int($1 * 0.75)}' <<<"${available_memory}")
else
	# reserve 1GB for the system and other processes
	available_memory=$((available_memory - 1024))
fi

# java -Xmx"${available_memory}"m -jar planetiler.jar --download --area=berlin --output=data/berlin.pmtiles


java -Xmx"${available_memory}"m -jar planetiler.jar --help