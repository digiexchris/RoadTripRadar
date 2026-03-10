FROM mcr.microsoft.com/devcontainers/java:21-bookworm

ARG ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=${ANDROID_SDK_ROOT}
ENV GRADLE_USER_HOME=/home/vscode/.gradle
ENV PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/build-tools/34.0.0:${PATH}"

RUN apt-get update && apt-get install -y --no-install-recommends \
        unzip \
        wget \
        usbutils \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && cd /tmp \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip \
    && unzip -q cmdline-tools.zip \
    && mv cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm cmdline-tools.zip

# build-tools 36 is used for compilation; build-tools 34 is used for signing
# (apksigner in 35+ breaks F-Droid reproducible builds)
RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager \
        "platform-tools" \
        "platforms;android-36" \
        "platforms;android-36.1" \
        "build-tools;34.0.0" \
        "build-tools;36.0.0"

RUN chmod -R a+rwX "${ANDROID_SDK_ROOT}"

# Pre-download Gradle distribution so the wrapper doesn't fetch it on first build
COPY gradle/wrapper/gradle-wrapper.jar /tmp/gradle-project/gradle/wrapper/
COPY gradle/wrapper/gradle-wrapper.properties /tmp/gradle-project/gradle/wrapper/
COPY gradlew /tmp/gradle-project/
RUN cd /tmp/gradle-project && chmod +x gradlew \
    && ./gradlew --version \
    && rm -rf /tmp/gradle-project \
    && chown -R vscode:vscode "${GRADLE_USER_HOME}"

# Install act (nektos) for local GitHub Actions testing
RUN curl -fsSL https://raw.githubusercontent.com/nektos/act/master/install.sh | bash -s -- -b /usr/local/bin
