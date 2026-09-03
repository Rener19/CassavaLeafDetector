import zipfile, re

apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

with zipfile.ZipFile(apk, 'r') as z:
    for name in ['classes.dex', 'classes2.dex', 'classes3.dex', 'classes4.dex']:
        data = z.read(name)
        # Search for MainActivity strings
        if b'MainActivity' in data:
            print(f'MainActivity found in {name}')
            # Look for metric strings
            for m in [b'Training Time', b'Multiply-Accumulate', b'Matthews', b'D-CLAHE']:
                if m in data:
                    print(f'  Found {m} in {name}')
