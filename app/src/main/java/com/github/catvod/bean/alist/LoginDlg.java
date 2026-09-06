package com.github.catvod.bean.alist;

import com.github.catvod.spider.Init;
import com.github.catvod.spider.Logger;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LoginDlg {

    private static final long TIMEOUT_MS = 60_000L; // 对话框最长等待时间，超时自动放弃，避免后台线程永久阻塞

    /**
     * 显示单输入框对话框（兼容旧的单字段场景），阻塞后台线程直到对话框关闭或超时。
     *
     * @param hint 输入框的提示文本
     * @return 用户输入的内容（取消/超时返回空字符串）
     */
    public static String showLoginDlg(String hint) {
        String[] result = showLoginDlg(hint, null);
        return result[0];
    }

    /**
     * 显示"用户名 + 密码"双输入框登录对话框，一次弹窗收集两个字段，阻塞后台线程直到对话框关闭或超时。
     * 密码框自动掩码；按返回键/取消/超时都会正常唤醒，不会造成线程卡死。
     *
     * @param usernameHint 用户名输入框提示
     * @param passwordHint 密码输入框提示（null 表示只需要单输入框）
     * @return {@code [0]=用户名, [1]=密码}，取消/超时则为空字符串
     */
    public static String[] showLoginDlg(final String usernameHint, final String passwordHint) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = { "", "" };
        try {
            Activity activity = Init.getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return result; // Activity 无效，直接返回空，不阻塞
            }

            // 在主线程显示对话框，延迟一步等待 Activity 就绪
            Init.run(() -> {
                try {
                    final EditText userInput = new EditText(activity);
                    userInput.setHint(usernameHint);
                    userInput.setSingleLine(true);

                    final LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(60, 30, 60, 0);
                    layout.addView(userInput);

                    final EditText passwordInput;
                    if (passwordHint != null) {
                        passwordInput = new EditText(activity);
                        passwordInput.setHint(passwordHint);
                        passwordInput.setSingleLine(true);
                        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); // 密码掩码
                        layout.addView(passwordInput);
                    } else {
                        passwordInput = null;
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle("登录设置")
                            .setMessage(passwordHint != null ? "请填写用户名与密码" : "请填写所需信息")
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setView(layout)
                            .setPositiveButton("确定", (dialog, which) -> {
                                result[0] = userInput.getText().toString();
                                if (passwordInput != null) {
                                    result[1] = passwordInput.getText().toString();
                                }
                                latch.countDown();
                            })
                            .setNegativeButton("取消", (dialog, which) -> latch.countDown())
                            .setOnCancelListener(dialog -> latch.countDown()); // 按返回键也会唤醒，杜绝卡死

                    AlertDialog dialog = builder.create();
                    dialog.show();
                    // 自动弹出软键盘
                    userInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                    }
                } catch (Exception e) {
                    Logger.log("登录对话框异常:" + e);
                    latch.countDown();
                }
            }, 500);

            // 阻塞后台线程，直到对话框关闭；带超时兜底
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Logger.log("登录对话框异常:" + e);
            return result;
        }
        return result;
    }
}