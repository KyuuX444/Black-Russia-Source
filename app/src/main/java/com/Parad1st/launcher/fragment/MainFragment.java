package com.byparad1st.launcher.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.appcompat.widget.AppCompatButton;

import androidx.fragment.app.Fragment;

import android.view.View.OnClickListener;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import com.byparad1st.game.R;
import com.byparad1st.launcher.activity.LoaderActivity;
import com.byparad1st.launcher.other.AlyaLiteDownloader;

import org.ini4j.Wini;

public class MainFragment extends Fragment {
	
	EditText nickname;
	ImageButton ib_info;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View inflate = inflater.inflate(R.layout.fragment_main, container, false);
    	Animation animation = AnimationUtils.loadAnimation(getActivity(), R.anim.button_click);
        
        InitLogic();
        
        nickname = inflate.findViewById(R.id.edit_text_name);
        ib_info = inflate.findViewById(R.id.ib_info);
		
	    ((AppCompatButton) inflate.findViewById(R.id.button_play)).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                v.startAnimation(animation);
                Timer t = new Timer();
                t.schedule(new TimerTask(){
                   @Override
                   public void run() {
                       if (getActivity() != null) {
                           getActivity().runOnUiThread(() -> onClickPlay());
                       }
                   }
                }, 200L);
            }
        });
        
        ((ImageButton) ib_info).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                v.startAnimation(animation);
                TextView info_nick = inflate.findViewById(R.id.text_view_info_about_nickname);
                if(info_nick.getVisibility() == View.INVISIBLE)
                    info_nick.setVisibility(View.VISIBLE);
                else 
                    info_nick.setVisibility(View.INVISIBLE);
            }
        });
        
        ((ImageButton) inflate.findViewById(R.id.button_vk)).setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
            	v.startAnimation(animation);
        	    Timer t = new Timer();
                t.schedule(new TimerTask(){
                   @Override
                   public void run() {
                       startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://github.com/KyuuX444/Black-Russia-Source")));
                   }
                }, 200L);
            }
        });
        
        ((ImageButton) inflate.findViewById(R.id.button_discord)).setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
            	v.startAnimation(animation);
        	    Timer t = new Timer();
                t.schedule(new TimerTask(){
                   @Override
                   public void run() {
                       startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://discord.com/")));
                   }
                }, 200L);
            }
        });
        
        ((ImageButton) inflate.findViewById(R.id.button_telegram)).setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
            	v.startAnimation(animation);
        	    Timer t = new Timer();
                t.schedule(new TimerTask(){
                   @Override
                   public void run() {
                       startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://github.com/KyuuX444/Black-Russia-Source")));
                   }
                }, 200L);
            }
        });
        
        ((AppCompatButton) inflate.findViewById(R.id.button_clean_game)).setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
            	v.startAnimation(animation);
        	    Timer t = new Timer();
                t.schedule(new TimerTask(){
                   @Override
                   public void run() {
                       /*MainActivity activity = getActivity();
		               activity.toLoaderFragment();*/
                   }
                }, 200L);
            }
        });
        
        
        ((EditText) nickname)
                .setOnEditorActionListener(
                        new EditText.OnEditorActionListener() {
                            @Override
                            public boolean onEditorAction(
                                    TextView v, int actionId, KeyEvent event) {
                                if (actionId == EditorInfo.IME_ACTION_SEARCH
                                        || actionId == EditorInfo.IME_ACTION_DONE
                                        || event.getAction() == KeyEvent.ACTION_DOWN
                                                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                                    try {
                                        File f =
                                                new File(
                                                        Environment.getExternalStorageDirectory()
                                                                + "/ryznSAMP/SAMP/settings.ini");
                                        if (!f.exists()) {
                                            f.createNewFile();
                                            f.mkdirs();
                                        }
                                        Wini w =
                                                new Wini(
                                                        new File(
                                                                Environment.getExternalStorageDirectory()
                                 + "/ryznSAMP/SAMP/settings.ini"));
								 if(checkValidNick()){
									 w.put("client", "name", nickname.getText().toString());
                                        Toast.makeText(
                                                getActivity(),
                                                "Nama karakter baru berhasil disimpan!",
                                                Toast.LENGTH_SHORT).show();
								 } else {
									 checkValidNick();
								 }
                                        w.store();
                                    } catch (IOException e) {
                                        e.printStackTrace();
										Toast.makeText(getActivity(), "Silakan instal game terlebih dahulu!", Toast.LENGTH_SHORT).show();
                                    }
                                }
                                return false;
                            }
        });
        
        nickname.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        
            }

             @Override
             public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                 try {
                     File f = new File(Environment.getExternalStorageDirectory() + "/ryznSAMP/SAMP/settings.ini");
                     if (!f.exists()) {
                         AlyaLiteDownloader.ensureClientConfigAndFonts(nickname.getText().toString());
                     } else {
                         Wini w = new Wini(f);
                         if (checkValidNick()) {
                             w.put("client", "name", nickname.getText().toString());
                             w.store();
                         }
                     }
                 } catch (IOException e) {
                     e.printStackTrace();
                 }
                 }

             @Override
             public void afterTextChanged(Editable editable) {
        
             }
        });
		
        return inflate;
    }
    
    public void onClickPlay() {
        if (getActivity() != null && !AlyaLiteDownloader.hasStoragePermission(getActivity())) {
            AlyaLiteDownloader.requestAllFilesPermissionIfNeeded(getActivity());
            return;
        }
        if (IsGameInstalled()) {
            AlyaLiteDownloader.ensureClientConfigAndFonts(nickname != null ? nickname.getText().toString() : "");
            startActivity(new Intent(getActivity(), com.byparad1st.game.core.GTASA.class));
        } else {
            startActivity(new Intent(getActivity(), LoaderActivity.class));
        }
    }
    
    private boolean IsGameInstalled()
    {
        String CheckFile = Environment.getExternalStorageDirectory() + "/ryznSAMP/texdb/gta3.img";
        File file = new File(CheckFile);
        return file.exists();
    }
	
	private void InitLogic() {
        try {
            Wini w = new Wini(new File(Environment.getExternalStorageDirectory() + "/ryznSAMP/SAMP/settings.ini"));
            nickname.setText(w.get("client", "name"));
            w.store();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	public boolean checkValidNick(){
		EditText nick = nickname != null ? nickname : (EditText) getActivity().findViewById(R.id.edit_text_name);
		if(nick == null || nick.getText().toString().isEmpty()) {
			Toast.makeText(getActivity(), "Masukkan nama karakter", Toast.LENGTH_SHORT).show();
			return false;
		}
		if(!(nick.getText().toString().contains("_"))){
			Toast.makeText(getActivity(), "Nama karakter harus mengandung simbol \"_\"", Toast.LENGTH_SHORT).show();
			return false;
		}
		if(nick.getText().toString().length() < 4){
			Toast.makeText(getActivity(), "Panjang nama karakter minimal 4 karakter", Toast.LENGTH_SHORT).show();
			return false;
		}
		return true;
	}
}