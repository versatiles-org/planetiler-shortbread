#!/bin/bash

display_help() {
	echo "Options:"
	echo "  -h, --help          Show this help message"
	echo "  -a, --area <name>   Specify the area to process (e.g.: \"planet\", \"geofabrik:ukraine\", etc.)"
}

AREA=""
SOURCE="osm"

while [[ $# -gt 0 ]]; do
	case $1 in
	-h | --help)
		display_help
		exit 0
		;;
	-a | --area)
		AREA="$2"
		shift # past argument
		shift # past value
		;;
	-* | --* | *)
		echo "Unknown option $1"
		exit 1
		;;
	esac
done

if [[ -z "$AREA" ]]; then
	echo "Error: Area must be specified with -a or --area option."
	display_help
	exit 1
fi

NAME=$SOURCE

if [[ "$AREA" != "planet" ]]; then
	NAME=$NAME-${AREA##*:}
fi

# get the available memory
available_memory=$(free -m | awk '/^Mem:/{print $7}')

if [ "$available_memory" -lt 4096 ]; then
	# use 75% of the available memory for processing
	available_memory=$(awk '{print int($1 * 0.75)}' <<<"${available_memory}")
else
	# reserve 1GB for the system and other processes
	available_memory=$((available_memory - 1024))
fi

if [ "$available_memory" -gt 130000 ]; then
	STORAGE="ram"
else
	STORAGE="mmap"
fi

java -Xmx"${available_memory}"m -jar planetiler.jar \
	--area=$AREA \
	--download \
	--download-threads=10 \
	--download-chunk-size-mb=1000 \
	--fetch-wikidata \
	--nodemap-type=sparsearray \
	--storage=$STORAGE \
	--output=data/$NAME.pmtiles \
	--force=true \
	config/shortbread.yml

versatiles convert -c brotli data/$NAME.pmtiles data/$NAME.versatiles

# java -Xmx"${available_memory}"m -jar planetiler.jar --help
