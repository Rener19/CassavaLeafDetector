with open('app/src/main/res/layout/layout_single_scan.xml', 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('xmlns:app="http://schemas.android.com/apk/res-auto"', 'xmlns:app="http://schemas.android.com/apk/res-auto"\n    xmlns:tools="http://schemas.android.com/tools"')
with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(content)
