import hashlib
import io
import zipfile

def compute_sha1_stream(data):
    digest = hashlib.sha1()
    digest.update(data)
    return digest.hexdigest()

def read_limited_bytes(data, limit):
    buffer = bytearray()
    total = 0
    stream = io.BytesIO(data)
    
    while True:
        chunk = stream.read(8192)
        if not chunk:
            break
        total += len(chunk)
        if total > limit:
            break
        buffer.extend(chunk)
    return bytes(buffer)

data = b"A" * (25 * 1024 * 1024 + 10) # 25MB + 10 bytes

sha1_stream = compute_sha1_stream(data)
sha1_limited = compute_sha1_stream(read_limited_bytes(data, 25 * 1024 * 1024))

print("Stream:", sha1_stream)
print("Limited:", sha1_limited)

