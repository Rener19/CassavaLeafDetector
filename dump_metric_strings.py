import zipfile, re

apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

with zipfile.ZipFile(apk, 'r') as z:
    data = z.read('classes3.dex')
    # Let's extract ASCII / UTF-8 strings around Training Time
    pos = data.find(b'Training Time:')
    if pos != -1:
        start = max(0, pos - 200)
        end = min(len(data), pos + 2500)
        chunk = data[start:end]
        # clean non-printable
        cleaned = ''.join(chr(b) if 32 <= b < 127 or b in (10, 13) else '\n' for b in chunk)
        print('=== STRINGS IN DEX ===')
        for line in cleaned.splitlines():
            line_s = line.strip()
            if len(line_s) > 4:
                print(line_s)
