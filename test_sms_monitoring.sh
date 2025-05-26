#!/bin/bash

echo "=== Catamaran SMS Monitoring & Sync Test Script ==="
echo "This script will monitor SMS detection and sync attempts"
echo "Updated intervals: 1-2 minutes instead of 30 minutes"
echo ""

# Clear previous logs
echo "Clearing previous logs..."
~/Library/Android/sdk/platform-tools/adb -s 38020DLJH00253 logcat -c

echo ""
echo "Starting real-time SMS monitoring and sync tracking..."
echo "Send an SMS to your device now and watch for logs below:"
echo "----------------------------------------"

# Monitor SMS and sync related logs with comprehensive filtering
~/Library/Android/sdk/platform-tools/adb -s 38020DLJH00253 logcat | grep -E "(SMSMonitor|SmsMonitor|DataSyncWorker|DataSyncService|MonitoringRepository|BackgroundMonitorService|sync|upload|HTTP|API|Failed|Error|network|catamaran)" --line-buffered | while read line; do
    timestamp=$(date '+%H:%M:%S')
    echo "[$timestamp] $line"
done 