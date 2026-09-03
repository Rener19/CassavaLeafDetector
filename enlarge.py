import re

with open('app/src/main/res/layout/layout_single_scan.xml', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the inner layout for Original Image
old_orig = '''                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:orientation="vertical"
                            android:gravity="center"
                            android:layout_marginEnd="4dp">
                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="Original Image"
                                android:textColor="@color/gray_text"
                                android:textSize="12sp"
                                android:layout_marginBottom="4dp"/>
                            <com.google.android.material.card.MaterialCardView
                                android:layout_width="120dp"
                                android:layout_height="120dp"
                                app:cardCornerRadius="8dp"
                                app:strokeWidth="0dp">
                                <ImageView
                                    android:id="@+id/img_scanned"
                                    android:layout_width="match_parent"
                                    android:layout_height="match_parent"
                                    android:scaleType="centerCrop"
                                    tools:src="@android:drawable/ic_menu_report_image"/>
                            </com.google.android.material.card.MaterialCardView>
                        </LinearLayout>'''

new_orig = '''                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:gravity="center"
                            android:layout_marginBottom="16dp">
                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="Original Image"
                                android:textColor="@color/gray_text"
                                android:textSize="14sp"
                                android:layout_marginBottom="4dp"/>
                            <com.google.android.material.card.MaterialCardView
                                android:layout_width="match_parent"
                                android:layout_height="300dp"
                                app:cardCornerRadius="8dp"
                                app:strokeWidth="0dp">
                                <ImageView
                                    android:id="@+id/img_scanned"
                                    android:layout_width="match_parent"
                                    android:layout_height="match_parent"
                                    android:scaleType="centerCrop"
                                    tools:src="@android:drawable/ic_menu_report_image"/>
                            </com.google.android.material.card.MaterialCardView>
                        </LinearLayout>'''

# Replace the inner layout for D-CLAHE Image
old_enh = '''                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:orientation="vertical"
                            android:gravity="center"
                            android:layout_marginStart="4dp">
                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="D-CLAHE Enhanced"
                                android:textColor="@color/accent_green"
                                android:textStyle="bold"
                                android:textSize="12sp"
                                android:layout_marginBottom="4dp"/>
                            <com.google.android.material.card.MaterialCardView
                                android:layout_width="120dp"
                                android:layout_height="120dp"
                                app:cardCornerRadius="8dp"
                                app:strokeWidth="0dp">
                                <ImageView
                                    android:id="@+id/img_enhanced"
                                    android:layout_width="match_parent"
                                    android:layout_height="match_parent"
                                    android:scaleType="centerCrop"
                                    tools:src="@android:drawable/ic_menu_report_image"/>
                            </com.google.android.material.card.MaterialCardView>
                        </LinearLayout>'''

new_enh = '''                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:gravity="center">
                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="D-CLAHE Enhanced"
                                android:textColor="@color/accent_green"
                                android:textStyle="bold"
                                android:textSize="14sp"
                                android:layout_marginBottom="4dp"/>
                            <com.google.android.material.card.MaterialCardView
                                android:layout_width="match_parent"
                                android:layout_height="300dp"
                                app:cardCornerRadius="8dp"
                                app:strokeWidth="0dp">
                                <ImageView
                                    android:id="@+id/img_enhanced"
                                    android:layout_width="match_parent"
                                    android:layout_height="match_parent"
                                    android:scaleType="centerCrop"
                                    tools:src="@android:drawable/ic_menu_report_image"/>
                            </com.google.android.material.card.MaterialCardView>
                        </LinearLayout>'''


# Fix the parent LinearLayout
old_parent_start = '''                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginBottom="16dp">'''
new_parent_start = '''                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:layout_marginBottom="16dp">'''

# Strip leading whitespaces to match properly
def norm(s):
    return re.sub(r'[ \t]+', '', s)

def replace_block(src, old, new):
    idx = norm(src).find(norm(old))
    if idx == -1:
        return src
    
    # We will use simple string matching line by line to be safe
    # But since whitespaces can vary, let's use regex
    escaped_old = re.escape(old)
    escaped_old = re.sub(r'\\ \n[ \t]*', r'\\s*', escaped_old)
    return re.sub(escaped_old, new, src)

# wait actually just use string replacement if we read/write the exact same spacing
content = content.replace(old_orig, new_orig)
content = content.replace(old_enh, new_enh)
content = content.replace(old_parent_start, new_parent_start)

with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(content)
