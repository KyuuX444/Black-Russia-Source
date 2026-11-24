package com.byparad1st.game.gui.parad1streg;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ToggleButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.nvidia.devtech.NvEventQueueActivity;
import com.byparad1st.game.R;
import com.byparad1st.game.gui.util.Utils;
import java.io.UnsupportedEncodingException;
import com.byparad1st.launcher.Registration.reg;

public class regandlogin {
    public Activity activity;
    public ConstraintLayout auth_layout, reg_layout, reg_layout_sx, finger_layout, finn_layout, text_layout, reg_person;
    public LinearLayout finger_button, Back_but, Back_but3;
    public FrameLayout frameLayout;
    public Button resume, rc2, resume2, back_scanner;
    public ToggleButton male, female;
    public EditText input, input2, input3, input4;
    public TextView nickname2, nickname, cancel, reg_info, reg_info2, reg_header, parad1stpon;
    public ImageView finger_but_parad1st;

    public ImageView person_left;
    public ImageView person_right;
    public ImageView person_close_h;
    public Button play_person;
    public TextView hint_person;

    public Switch regauto;
    static Boolean isTurned = false;
    static Boolean isReg = false;
    static int isAL = 0;

    public regandlogin(Activity aactivity) {
        activity = aactivity;
        auth_layout = aactivity.findViewById(R.id.parad1st_layout_login);
        auth_layout.setVisibility(View.GONE);
        reg_layout = aactivity.findViewById(R.id.parad1st_reg);
        reg_layout.setVisibility(View.GONE);
        reg_layout_sx = aactivity.findViewById(R.id.linearLayout4regsx);
        reg_layout_sx.setVisibility(View.GONE);

        reg_info = aactivity.findViewById(R.id.reg_info);
        reg_info.setText("Masukkan kata sandi untuk masuk ke game.\n\nInfo: jika akun ini bukan milik Anda,\nkeluarlah dari game dan ganti\nnama karakter Anda dengan yang baru!");
        reg_info2 = aactivity.findViewById(R.id.textView8);
        reg_info2.setText("Isi semua kolom untuk mulai bermain\n \n  •Kata sandi harus 6 hingga 16 karakter\n \n  •Kata sandi peka huruf besar/kecil");

        reg_header = aactivity.findViewById(R.id.reg_header);
        parad1stpon = aactivity.findViewById(R.id.parad1st_pon);

        finger_layout = aactivity.findViewById(R.id.finger_layout);
        frameLayout = aactivity.findViewById(R.id.frameLayout);
        Back_but3 = aactivity.findViewById(R.id.back_but3);
        Back_but = aactivity.findViewById(R.id.back_but);
        finger_but_parad1st = aactivity.findViewById(R.id.finger_but_parad1st);
        finn_layout = aactivity.findViewById(R.id.fin_parad1st);
        back_scanner = aactivity.findViewById(R.id.back_scanner);

        reg_person = aactivity.findViewById(R.id.reg_personil);
        reg_person.setVisibility(View.GONE);
        person_left = aactivity.findViewById(R.id.arrow_left_pers);
        person_right = aactivity.findViewById(R.id.arrow_right_pers);
        play_person = aactivity.findViewById(R.id.play_buttonnon);
        person_close_h = aactivity.findViewById(R.id.person_close_h);
        hint_person = aactivity.findViewById(R.id.hint_person);

        person_close_h.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hint_person.setVisibility(View.GONE);
                person_close_h.setVisibility(View.GONE);
            }
        });

        person_right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NvEventQueueActivity.getInstance().personclick(2);
            }
        });
        person_left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NvEventQueueActivity.getInstance().personclick(1);
            }
        });
        play_person.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NvEventQueueActivity.getInstance().personclick(0);
            }
        });


        finger_button = aactivity.findViewById(R.id.fingerprint_but);
        finger_button.setOnClickListener(view -> {
            view.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
            finger_layout.setVisibility(View.VISIBLE);
            frameLayout.setVisibility(View.GONE);
            resume.setVisibility(View.GONE);
            input.setVisibility(View.GONE);
            finger_button.setVisibility(View.GONE);
            Back_but.setVisibility(View.GONE);
        });

        Back_but3.setOnClickListener(view -> {
            view.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
            finger_layout.setVisibility(View.GONE);
            frameLayout.setVisibility(View.VISIBLE);
            resume.setVisibility(View.VISIBLE);
            input.setVisibility(View.VISIBLE);
            finger_button.setVisibility(View.VISIBLE);
            cancel.setVisibility(View.VISIBLE);
        });

        back_scanner.setOnClickListener(view -> {
            view.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
            finn_layout.setVisibility(View.GONE);

        });

        finger_but_parad1st.setOnClickListener(view -> {
            view.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
            NvEventQueueActivity.getInstance().showNotification(2, "Terjadi kesalahan, coba lagi nanti", 4, "", "");
            finn_layout.setVisibility(View.VISIBLE);
        });

        regauto = aactivity.findViewById(R.id.auto_switch);
        regauto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                    if(isChecked) {
                        isTurned = true;
                        reg.setAL(true);
                        NvEventQueueActivity.getInstance().showNotification(2, "Sementara tidak tersedia", 4, "", "");
                    } else {
                        isTurned = false;
                        reg.setAL(false);
                    }
            }
        });

        nickname = aactivity.findViewById(R.id.reg_nickname);
        nickname.setText(reg.getNick() + " " + "[" + reg.GetID() + "]");
        nickname2 = aactivity.findViewById(R.id.reg_nickname2);
        nickname2.setText(reg.getNick() + " " + "[" + reg.GetID() + "]");

        input = aactivity.findViewById(R.id.password_enter);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    reg.setPassword(String.valueOf(input.getText()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        input2 = aactivity.findViewById(R.id.password_enter3);
        input2.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        input3 = aactivity.findViewById(R.id.password_enter2);
        input3.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        input4 = aactivity.findViewById(R.id.mail_enter);
        input4.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        cancel = aactivity.findViewById(R.id.parad1st_pon);
        cancel.setOnClickListener(view -> {
            view.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
            try {
                NvEventQueueActivity.getInstance().sendDialogResponse(0, 1, reg.GetListitemToSend(), input.getText().toString().getBytes("windows-1251"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            HideAuth();
        });

        resume = aactivity.findViewById(R.id.play_but);
        resume.setOnClickListener(view -> {
            view.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
            try {
                if(checkValid()) {
                    NvEventQueueActivity.getInstance().sendDialogResponse(1, 1, reg.GetListitemToSend(), input.getText().toString().getBytes("windows-1251"));
                    HideAuth();
                }
            } catch (UnsupportedEncodingException e) {
                NvEventQueueActivity.getInstance().showNotification(2, "Terjadi kesalahan tidak diketahui", 5, "", "");
                e.printStackTrace();
            }
        });

        male = aactivity.findViewById(R.id.toggleButton);
        female = aactivity.findViewById(R.id.toggleButton2);

        rc2 = aactivity.findViewById(R.id.play_but23);
        resume2 = aactivity.findViewById(R.id.play_but222);
        text_layout = aactivity.findViewById(R.id.text_layout);
        resume2.setOnClickListener(view -> {
            try {
                if(checkValidReg() && input2.getText().toString().equals(input3.getText().toString())) {
                    NvEventQueueActivity.getInstance().sendDialogResponse(1, 2, reg.GetListitemToSend(), input2.getText().toString().getBytes("windows-1251"));
                    resume2.setText("LANJUTKAN");
                    input3.setInputType(View.AUTOFILL_TYPE_TEXT);
                    input3.setHint("Nama karakter pengundang");
                    NvEventQueueActivity.getInstance().showNotification(2, "Langkah opsional pendaftaran, dapat dilewati", 5, "", "");
                    input3.setText("");
                    input2.setVisibility(View.GONE);
                    input4.setVisibility(View.GONE);
                    reg_info2.setText("Nama teman harus menggunakan \nformat: Nick_Name");
                    reg_header.setText("APAKAH TEMAN MENGUNDANGMU?");
                    parad1stpon.setVisibility(View.VISIBLE);
                    text_layout.setVisibility(View.VISIBLE);

                    rc2.setVisibility(View.VISIBLE);
                    rc2.setOnClickListener(view3 -> {
                        view3.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
                        try {
                            NvEventQueueActivity.getInstance().sendDialogResponse(0, 2, reg.GetListitemToSend(), input3.getText().toString().getBytes("windows-1251"));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        HideReg();
                        ShowRegSX();
                        male.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                buttonView.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
                                if (buttonView.isChecked()) {
                                    try {
                                        NvEventQueueActivity.getInstance().sendDialogResponse(1, 2, reg.GetListitemToSend(), input3.getText().toString().getBytes("windows-1251"));
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                    HideRegSX();

                                } else {

                                }
                            }
                        });
                        female.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                buttonView.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
                                if (buttonView.isChecked()) {
                                    try {
                                        NvEventQueueActivity.getInstance().sendDialogResponse(0, 2, reg.GetListitemToSend(), input3.getText().toString().getBytes("windows-1251"));
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                    HideRegSX();
                                } else {

                                }
                            }
                        });
                    });

                    resume2.setOnClickListener(view2 -> {
                        view2.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
                        try {
                            NvEventQueueActivity.getInstance().sendDialogResponse(1, 2, reg.GetListitemToSend(), input3.getText().toString().getBytes("windows-1251"));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        HideReg();
                    });
                } else NvEventQueueActivity.getInstance().showNotification(2, "Kata sandi tidak cocok", 5, "", "");
            } catch (UnsupportedEncodingException e) {
                NvEventQueueActivity.getInstance().showNotification(2, "Terjadi kesalahan tidak diketahui", 5, "", "");
                e.printStackTrace();
            }
        });
    }

    public void ShowAuth()
    {
        Utils.ShowLayout(auth_layout, true);
        isReg = false;
    }

    public void HideAuth()
    {
        Utils.HideLayout(auth_layout, true);
    }

    public void ShowReg()
    {
        isReg = true;
        Utils.ShowLayout(reg_layout, true);
    }

    public void HideReg()
    {
        isReg = false;
        Utils.HideLayout(reg_layout, true);
    }

    public void ShowRegSX()
    {
        isReg = true;
        Utils.ShowLayout(reg_layout_sx, true);
    }

    public void HideRegSX()
    {
        Utils.HideLayout(reg_layout_sx, true);
        NvEventQueueActivity.getInstance().showNotification(3, "Selamat datang!", 4, "", "");
    }

    public void ShowPerson(){
        Utils.ShowLayout(reg_person, true);
    }
    public void HidePerson(){
        Utils.HideLayout(reg_person, false);
    }

    public boolean checkValid(){
        EditText nick = activity.findViewById(R.id.password_enter);
        if(nick.getText().toString().isEmpty()) {
            NvEventQueueActivity.getInstance().showNotification(2, "Masukkan kata sandi", 5, "", "");
            return false;
        }
        if(nick.getText().toString().length() < 6) {
            NvEventQueueActivity.getInstance().showNotification(2, "Panjang kata sandi minimal 6 karakter", 5, "", "");
            return false;
        }
        return true;
    }

    public boolean checkValidReg() {
        EditText nick2 = activity.findViewById(R.id.password_enter2);
        EditText nick3 = activity.findViewById(R.id.password_enter3);
        if(nick2.getText().toString().isEmpty()) {
            NvEventQueueActivity.getInstance().showNotification(2, "Masukkan kata sandi", 5, "", "");
            return false;
        }
        if(nick2.getText().toString().length() < 6 && !nick2.getText().toString().isEmpty()) {
            NvEventQueueActivity.getInstance().showNotification(2, "Panjang kata sandi minimal 6 karakter", 5, "", "");
            return false;
        }
        if(nick3.getText().toString().isEmpty()) {
            NvEventQueueActivity.getInstance().showNotification(2, "Ulangi kata sandi", 5, "", "");
            return false;
        }
        return true;
    }
}
