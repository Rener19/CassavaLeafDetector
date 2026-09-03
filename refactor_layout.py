import sys

with open('app/src/main/res/layout/activity_main.xml', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Extract lines from 27 to 587 (0-indexed 26 to 587) for layout_single_scan.xml
# Note: Ensure the xmlns attributes are added to the root layout if needed, but since it's an include, it might not strictly need them if they don't use undefined namespaces, but it's safer to add them.
single_scan_content = '<?xml version="1.0" encoding="utf-8"?>\n'
single_scan_content += lines[26].replace('<androidx.core.widget.NestedScrollView', 
    '<androidx.core.widget.NestedScrollView\n    xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:app="http://schemas.android.com/apk/res-auto"') 
single_scan_content += ''.join(lines[27:588])

with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(single_scan_content)

# Now create the new activity_main.xml
new_activity_main = ''.join(lines[0:26]) + '''
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior"
        android:layout_marginBottom="56dp">
        
        <include layout="@layout/layout_single_scan"
            android:id="@+id/layout_single_scan" />
            
        <include layout="@layout/layout_batch_test"
            android:id="@+id/layout_batch_test"
            android:visibility="gone" />
            
    </FrameLayout>
    
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_navigation"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:background="?android:attr/windowBackground"
        app:menu="@menu/bottom_nav_menu" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
'''

with open('app/src/main/res/layout/activity_main.xml', 'w', encoding='utf-8') as f:
    f.write(new_activity_main)
