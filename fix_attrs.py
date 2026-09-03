with open('app/src/main/res/layout/layout_single_scan.xml', 'r', encoding='utf-8') as f:
    c = f.read()

# Fix style attribute: android:style -> style
c = c.replace('android:style=', 'style=')

# Fix progressTint and progressBackgroundTint: app:progressTint -> android:progressTint
c = c.replace('app:progressTint=', 'android:progressTint=')
c = c.replace('app:progressBackgroundTint=', 'android:progressBackgroundTint=')

with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(c)
