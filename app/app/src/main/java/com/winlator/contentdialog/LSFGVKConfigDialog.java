package com.winlator.contentdialog;

import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.core.AppUtils;
import com.winlator.widget.SeekBar;

public class LSFGVKConfigDialog extends ContentDialog {
    public LSFGVKConfigDialog(XServerDisplayActivity activity) {
        super(activity, R.layout.lsfg_vk_config_dialog);
        setTitle(R.string.lsfg_vk);
        setIcon(R.drawable.icon_screen_effect);

        Spinner sMultiplier = findViewById(R.id.SLSFGMultiplier);
        SeekBar sbFlowScale = findViewById(R.id.SBLSFGFlowScale);
        CheckBox cbPerformanceMode = findViewById(R.id.CBLSFGPerformanceMode);
        String[] multiplierItems = {
            activity.getString(R.string.lsfg_off),
            activity.getString(R.string.lsfg_multiplier_2x),
            activity.getString(R.string.lsfg_multiplier_3x),
            activity.getString(R.string.lsfg_multiplier_4x)
        };
        sMultiplier.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, multiplierItems));

        int multiplier = activity.getLSFGMultiplier();
        sMultiplier.setSelection(multiplier == 1 ? 0 : multiplier - 1);
        sbFlowScale.setValue(activity.getLSFGFlowScale());
        cbPerformanceMode.setChecked(activity.isLSFGPerformanceMode());

        setOnConfirmCallback(() -> {
            int position = sMultiplier.getSelectedItemPosition();
            int selectedMultiplier = position == 0 ? 1 : position + 1;
            if (!activity.applyLSFGVKConfig(selectedMultiplier, sbFlowScale.getValue(), cbPerformanceMode.isChecked())) {
                AppUtils.showToast(activity, R.string.unable_to_apply_lsfg_settings);
            }
        });
    }
}
