def generate_class_card(class_id, class_name, color_res):
    return f"""
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp"
            app:strokeColor="{color_res}"
            app:strokeWidth="1dp">
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:padding="12dp"
                android:gravity="center_vertical">
                
                <View android:layout_width="16dp" android:layout_height="16dp" android:background="{color_res}" android:layout_marginEnd="12dp" />
                
                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="{class_name}"
                        android:textStyle="bold"
                        android:textColor="@color/black" />
                    <TextView
                        android:id="@+id/txt_count_{class_id}"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="0 images selected"
                        android:textSize="12sp" />
                </LinearLayout>
                
                <Button
                    android:id="@+id/btn_upload_{class_id}"
                    style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Upload"
                    app:strokeColor="{color_res}"
                    android:textColor="{color_res}" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
"""

classes = [
    ('cbb', 'Blight (CBB)', '@color/color_cbb'),
    ('cbsd', 'Streak (CBSD)', '@color/color_cbsd'),
    ('cgm', 'Mottle (CGM)', '@color/color_cgm'),
    ('cmd', 'Mosaic (CMD)', '@color/color_cmd'),
    ('healthy', 'Healthy', '@color/color_healthy')
]

xml_content = """<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:clipToPadding="false"
    android:paddingBottom="40dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Batch Test Models"
            android:textColor="@color/black"
            android:textSize="22sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Upload multiple images into each ground truth class."
            android:layout_marginBottom="16dp" />
"""

for c in classes:
    xml_content += generate_class_card(c[0], c[1], c[2])

xml_content += """
        <Button
            android:id="@+id/btn_run_batch_test"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:text="Run Test"
            android:layout_marginTop="16dp"
            android:layout_marginBottom="24dp"
            app:cornerRadius="12dp" />
            
        <ProgressBar
            android:id="@+id/progress_batch"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:visibility="gone"
            android:layout_marginBottom="24dp" />

        <LinearLayout
            android:id="@+id/layout_batch_results"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:visibility="gone">
            
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Test Results"
                android:textColor="@color/black"
                android:textSize="18sp"
                android:textStyle="bold"
                android:layout_marginBottom="12dp" />
                
            <TextView
                android:id="@+id/txt_batch_metrics"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textColor="@color/black"
                android:background="@color/light_green_bg"
                android:padding="16dp"
                android:lineSpacingExtra="4dp"
                android:layout_marginBottom="24dp" />
        </LinearLayout>
        
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginBottom="12dp">
            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Batch Test History"
                android:textColor="@color/black"
                android:textSize="18sp"
                android:textStyle="bold" />
            <Button
                android:id="@+id/btn_clear_batch_history"
                style="@style/Widget.MaterialComponents.Button.TextButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Clear All"
                android:textColor="@color/gray_text" />
        </LinearLayout>
            
        <TextView
            android:id="@+id/txt_no_batch_history"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginVertical="24dp"
            android:visibility="gone"
            android:text="No past batch tests found."
            android:textAlignment="center"
            android:textColor="@color/gray_text"
            android:textSize="14sp" />
            
        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recycler_batch_history"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:nestedScrollingEnabled="false" />
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
"""

with open('app/src/main/res/layout/layout_batch_test.xml', 'w', encoding='utf-8') as f:
    f.write(xml_content)
