import zipfile, os

apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

with zipfile.ZipFile(apk, 'r') as z:
    for f in ['classes.dex', 'classes2.dex', 'classes3.dex', 'classes4.dex']:
        data = z.read(f)
        for term in [b'label_original', b'card_scanned', b'D-CLAHE View']:
            if term in data:
                print(f'{term} in {f}')
