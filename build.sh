#!/bin/bash

# Weather Radar App - Build Script (Bash)
# This script builds the app and syncs it with Android

set -e  # Exit on error

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Default options
OPEN_ANDROID_STUDIO=false
SKIP_BUILD=false

# Help function
show_help() {
    echo "Weather Radar App - Build Script"
    echo ""
    echo "Usage: ./build.sh [options]"
    echo ""
    echo "Options:"
    echo "    -o, --open        Open Android Studio after syncing"
    echo "    -s, --skip-build  Skip the npm build step (useful if already built)"
    echo "    -h, --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "    ./build.sh                    # Build and sync"
    echo "    ./build.sh --open             # Build, sync, and open Android Studio"
    echo "    ./build.sh --skip-build       # Skip build, just sync to Android"
    echo ""
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -o|--open)
            OPEN_ANDROID_STUDIO=true
            shift
            ;;
        -s|--skip-build)
            SKIP_BUILD=true
            shift
            ;;
        -h|--help)
            show_help
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Weather Radar App - Build Script${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

# Check if we're in the correct directory
if [ ! -f "package.json" ]; then
    echo -e "${RED}Error: package.json not found!${NC}"
    echo -e "${YELLOW}Please run this script from the weather-radar directory${NC}"
    exit 1
fi

# Step 1: Build the React app
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${GREEN}[1/3] Building React app...${NC}"
    npm run build
    echo -e "${GREEN}Build completed successfully!${NC}"
    echo ""
else
    echo -e "${YELLOW}[1/3] Skipping build step...${NC}"
    echo ""
fi

# Step 2: Sync with Capacitor
echo -e "${GREEN}[2/3] Syncing with Android platform...${NC}"
npx cap sync android
echo -e "${GREEN}Sync completed successfully!${NC}"
echo ""

# Step 3: Optionally open Android Studio
if [ "$OPEN_ANDROID_STUDIO" = true ]; then
    echo -e "${GREEN}[3/3] Opening Android Studio...${NC}"
    if npx cap open android; then
        echo -e "${GREEN}Android Studio opened${NC}"
    else
        echo -e "${YELLOW}Warning: Could not open Android Studio automatically${NC}"
        echo -e "${YELLOW}You can manually open: android/${NC}"
    fi
else
    echo -e "${GREEN}[3/3] Done!${NC}"
    echo ""
    echo -e "${CYAN}Next steps:${NC}"
    echo -e "${WHITE}  1. Open Android Studio${NC}"
    echo -e "${WHITE}  2. Open the 'android' folder in this project${NC}"
    echo -e "${WHITE}  3. Click the Run button to build and deploy${NC}"
    echo ""
    echo -e "${YELLOW}Or run: ./build.sh --open${NC}"
fi

echo ""
echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Build process completed!${NC}"
echo -e "${CYAN}========================================${NC}"
