package se.arctosoft.vault.utils;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

public class ViewAnimations {

    private static float lastTouchY;
    private static final float RESISTANCE = 0.15f; // Чуть увеличил для приятного отклика
    private static final float MAX_DRAG = 60f;      // Немного больше хода для красоты

    @SuppressLint("ClickableViewAccessibility")
    public static void setupElasticLogo(View container, View logo) {
        container.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaY = event.getRawY() - lastTouchY;
                    if (deltaY > 0) {
                        float drag = Math.min(deltaY * RESISTANCE, MAX_DRAG);
                        logo.setTranslationY(drag);
                        // Эффект легкого растяжения при натяжении
                        logo.setScaleY(1f + (drag * 0.002f));
                        logo.setScaleX(1f - (drag * 0.001f));
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float currentY = logo.getTranslationY();

                    // Возврат с пружиной
                    logo.animate()
                        .translationY(0)
                        .scaleY(1f)
                        .scaleX(1f)
                        .setInterpolator(new OvershootInterpolator(4f))
                        .setDuration(400)
                        .start();

                    // Если был просто короткий тап (почти не тянули) — срабатывает клик
                    if (currentY < 15) {
                        v.performClick();
                    }
                    break;
            }
            return true;
        });
    }

    // Тряска при ошибке или клике
    public static void shakeView(View view) {
        ObjectAnimator shaker = ObjectAnimator.ofFloat(view, "translationX", 0, 20, -20, 20, -20, 15, -15, 0);
        shaker.setDuration(400);
        shaker.start();
    }
}
