import re

with open('app/src/main/res/layout/layout_single_scan.xml', 'r', encoding='utf-8') as f:
    content = f.read()

# find the last MaterialCardView and insert the button before it
target = '''            <!-- History Card -->
            <com.google.android.material.card.MaterialCardView'''

if target not in content:
    # Just find <com.google.android.material.card.MaterialCardView and pick the last one.
    parts = content.rsplit('<com.google.android.material.card.MaterialCardView', 1)
    if len(parts) == 2:
        new_content = parts[0] + '''
            <Button
                android:id="@+id/btn_view_thesis_metrics"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="View Full Thesis Evaluation Report"
                android:layout_marginBottom="16dp"
                app:cornerRadius="8dp"
                android:backgroundTint="@color/accent_green" />

            <com.google.android.material.card.MaterialCardView''' + parts[1]
        
        with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
            f.write(new_content)
