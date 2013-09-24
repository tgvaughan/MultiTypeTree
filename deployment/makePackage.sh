#!/bin/bash
#
# Assemble BEAST 2 package
#

PKGNAME=MultiTypeTree

rm -rf $PKGNAME $PKGNAME.zip
mkdir $PKGNAME
mkdir $PKGNAME/examples
mkdir $PKGNAME/lib
mkdir $PKGNAME/doc

# Create source jar file
pushd ..
jar cf $PKGNAME.src.jar src test
popd
mv ../$PKGNAME.src.jar $PKGNAME

# Copy over examples
cp ../examples/simulated_data/full_inference.xml $PKGNAME/examples

# Copy over binaries
cp ../dist/MultiTypeTree.jar $PKGNAME/lib
cp ../lib/*.jar $PKGNAME/lib

# Copy over documentation

# Create version.xml
cat <<EOF > $PKGNAME/version.xml
<addon name="MultiTypeTree" version="1.0.0">
    <depends on="beast2" atleast="2.0.2"/>
</addon>

EOF

# Create archive and clean up
zip -r $PKGNAME.zip $PKGNAME
rm -rf $PKGNAME
