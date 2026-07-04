export RAW="Hello World!"
export ENCODED=$(echo -n "$RAW" | base64)

echo "Encoded: $ENCODED"

# Add spaces
export SPACED="S G V s b G 8 g V 2 9 y b G Q h"

echo "Decoding SPACED..."
echo "$SPACED" | base64 -d
echo

echo "Decoding without quotes..."
echo $ENCODED | base64 -d
echo
