#!/bin/bash


cp -r Lunii-Transfert.app target/
cp target/studio.sh target/Lunii-Transfert.app/Contents/MacOS
cp target/lunii.png target/Lunii-Transfert.app/Contents/Resources
cp -r target/lib/ target/Lunii-Transfert.app/Contents/Resources/Java/lib
cp libusb4java-1.3.0-darwin-aarch64.jar target/Lunii-Transfert.app/Contents/Resources/Java/lib/
cp target/$1.jar target/Lunii-Transfert.app/Contents/Resources/Java

rm -rf /tmp/buildlunii
mkdir -p /tmp/buildlunii
cp -r target/Lunii-Transfert.app /tmp/buildlunii
ln -s /Applications/ /tmp/buildlunii/Applications

hdiutil create -fs HFS+ -srcfolder "/tmp/buildlunii" -volname Lunii-Transfert target/Lunii-Transfert.dmg

rm -rf /tmp/buildlunii