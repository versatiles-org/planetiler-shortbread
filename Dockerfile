FROM debian:unstable

# copy versatiles and selftest
WORKDIR /app

RUN set -eux; \
    apt-get update -qq; \
    apt-get install -yq --no-install-recommends openjdk-21-jdk curl sudo; \
    curl -Ls "https://github.com/onthegomap/planetiler/releases/latest/download/planetiler.jar" -o planetiler.jar; \
    curl -Ls "https://github.com/versatiles-org/versatiles-rs/raw/main/scripts/install-unix.sh" | sudo sh; \
    apt-get purge -y --auto-remove curl sudo || true; \
    apt-get clean; \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

COPY resources .

ENTRYPOINT ["bash", "./run.sh"]
