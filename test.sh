export ENCODED_KEYSTORE="abc
def
ghi"

echo $ENCODED_KEYSTORE > out1.txt
printf "%s" "$ENCODED_KEYSTORE" > out2.txt
echo "$ENCODED_KEYSTORE" > out3.txt
