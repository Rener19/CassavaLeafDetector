with open('activity_main_backup.xml', 'r', encoding='utf-8') as f:
    lines = f.readlines()

single_scan_content = '<?xml version="1.0" encoding="utf-8"?>\n'
single_scan_content += lines[26].replace('<androidx.core.widget.NestedScrollView', 
    '<androidx.core.widget.NestedScrollView\n    xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:app="http://schemas.android.com/apk/res-auto"') 
single_scan_content += ''.join(lines[27:588])

with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(single_scan_content)
