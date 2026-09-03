with open('activity_main_backup.xml', 'r', encoding='utf-16') as f:
    content = f.read()

# Extract from <androidx.core.widget.NestedScrollView to </androidx.core.widget.NestedScrollView>
start_tag = '<androidx.core.widget.NestedScrollView'
end_tag = '</androidx.core.widget.NestedScrollView>'
start_idx = content.find(start_tag)
end_idx = content.find(end_tag) + len(end_tag)

nested_scroll_view_content = content[start_idx:end_idx]

# Inject namespaces
nested_scroll_view_content = nested_scroll_view_content.replace('<androidx.core.widget.NestedScrollView',
    '<androidx.core.widget.NestedScrollView\n    xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:app="http://schemas.android.com/apk/res-auto"\n    xmlns:tools="http://schemas.android.com/tools"')

single_scan_content = '<?xml version="1.0" encoding="utf-8"?>\n' + nested_scroll_view_content

with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(single_scan_content)

# Now rewrite activity_main.xml to use includes
new_activity_main = content[:start_idx] + '''<FrameLayout
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
''' + content[end_idx:]

with open('app/src/main/res/layout/activity_main.xml', 'w', encoding='utf-8') as f:
    f.write(new_activity_main)
