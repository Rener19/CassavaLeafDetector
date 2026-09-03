import re

with open('app/src/main/res/layout/layout_single_scan.xml', 'r', encoding='utf-8') as f:
    content = f.read()

start_str = "<androidx.constraintlayout.widget.ConstraintLayout"
end_str = "</androidx.constraintlayout.widget.ConstraintLayout>"

new_block = '''<androidx.constraintlayout.widget.ConstraintLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginBottom="20dp">

                        <TextView
                            android:id="@+id/label_original"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Original"
                            android:textColor="@color/gray_text"
                            android:textSize="14sp"
                            android:layout_marginBottom="6dp"
                            app:layout_constraintTop_toTopOf="parent"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintBottom_toTopOf="@id/card_scanned" />

                        <com.google.android.material.card.MaterialCardView
                            android:id="@+id/card_scanned"
                            android:layout_width="match_parent"
                            android:layout_height="0dp"
                            app:cardCornerRadius="12dp"
                            app:strokeWidth="1dp"
                            app:strokeColor="#E0E0E0"
                            app:layout_constraintDimensionRatio="4:3"
                            app:layout_constraintTop_toBottomOf="@id/label_original"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintEnd_toEndOf="parent">
                            <ImageView
                                android:id="@+id/img_scanned"
                                android:layout_width="match_parent"
                                android:layout_height="match_parent"
                                android:scaleType="centerCrop" />
                        </com.google.android.material.card.MaterialCardView>

                        <TextView
                            android:id="@+id/label_enhanced"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="D-CLAHE View"
                            android:textColor="@color/accent_green"
                            android:textStyle="bold"
                            android:textSize="14sp"
                            android:layout_marginTop="16dp"
                            android:layout_marginBottom="6dp"
                            app:layout_constraintTop_toBottomOf="@id/card_scanned"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintBottom_toTopOf="@id/card_enhanced" />

                        <com.google.android.material.card.MaterialCardView
                            android:id="@+id/card_enhanced"
                            android:layout_width="match_parent"
                            android:layout_height="0dp"
                            app:cardCornerRadius="12dp"
                            app:strokeWidth="1dp"
                            app:strokeColor="#E0E0E0"
                            app:layout_constraintDimensionRatio="4:3"
                            app:layout_constraintTop_toBottomOf="@id/label_enhanced"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintEnd_toEndOf="parent">
                            <ImageView
                                android:id="@+id/img_enhanced"
                                android:layout_width="match_parent"
                                android:layout_height="match_parent"
                                android:scaleType="centerCrop" />
                        </com.google.android.material.card.MaterialCardView>
                    </androidx.constraintlayout.widget.ConstraintLayout>'''

pattern = re.compile(re.escape(start_str) + r'.*?' + re.escape(end_str), re.DOTALL)
content = pattern.sub(new_block, content)

with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(content)
