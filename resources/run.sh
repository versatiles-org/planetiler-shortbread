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
MEM_FREE=$(free -m | awk '/^Mem:/{print $7}')

if [ "$MEM_FREE" -lt 2000 ]; then
	echo "Error: Not enough memory available. At least 2GB is required."
	exit 1
else
	MEM_USE=$(awk '{print int($1 - ($1 ^ 0.25) * 200)}' <<<"${MEM_USE}")
fi

if [ "$MEM_USE" -gt 130000 ]; then
	STORAGE="ram"
else
	STORAGE="mmap"
fi

java -Xmx"${MEM_USE}"m -jar planetiler.jar config/shortbread.yml \
	--area=$AREA \
	--download \
	--download-chunk-size-mb=1000 \
	--download-threads=10 \
	--fetch-wikidata \
	--force=true \
	--nodemap-type=sparsearray \
	--output=data/$NAME.pmtiles \
	--storage=$STORAGE

versatiles convert -c brotli data/$NAME.pmtiles data/$NAME.versatiles
