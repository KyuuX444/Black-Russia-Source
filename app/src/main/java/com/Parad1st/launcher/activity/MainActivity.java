package com.byparad1st.launcher.activity;

import android.Manifest;
import android.os.Bundle;
import android.os.Environment;
import android.content.pm.PackageManager;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;

import android.view.View.OnClickListener;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation;

import com.byparad1st.game.R;
import com.byparad1st.launcher.Registration.reg;
import com.byparad1st.launcher.other.AlyaLiteDownloader;

import org.ini4j.Wini;

import java.io.File;

import java.io.IOException;
import java.util.*;

public class MainActivity extends AppCompatActivity {
	
	EditText nickname;
	EditText serverAddress;
	ImageButton ib_info;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_main);
		
		Animation animation = AnimationUtils.loadAnimation(this, R.anim.button_click);
		
		if (android.os.Build.VERSION.SDK_INT >= 30) {
			if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
				requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1000);
			}
			if (!AlyaLiteDownloader.hasStoragePermission(this)) {
				AlyaLiteDownloader.requestAllFilesPermissionIfNeeded(this);
			}
		} else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 1000);
        }
        
        InitLogic();
        LoadNick();
        
        nickname = findViewById(R.id.edit_text_name);
        serverAddress = findViewById(R.id.edit_text_server);
        ib_info = findViewById(R.id.ib_info);
        if (serverAddress != null) {
            serverAddress.setText(AlyaLiteDownloader.getSavedServerAddress());
            serverAddress.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getAction() == KeyEvent.ACTION_DOWN
                                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    if (AlyaLiteDownloader.saveServerConfig(serverAddress.getText().toString(), null)) {
                        tost("Alamat server berhasil disimpan!");
                    }
                }
                return false;
            });
        }
		
	    ((AppCompatButton) findViewById(R.id.button_play)).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                v.startAnimation(animation);
                Timer t = new Timer();
                t.schedule(new TimerTask(){
                   @Override
                   public void run() {
                       runOnUiThread(() -> onClickPlay());
                   }
                }, 200L);
            }
        });
        
        ((ImageButton) ib_info).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                v.startAnimation(animation);
                TextView info_nick = findViewById(R.id.text_view_info_about_nickname);
                if(info_nick.getVisibility() == View.INVISIBLE)
                    info_nick.setVisibility(View.VISIBLE);
                else 
                    info_nick.setVisibility(View.INVISIBLE);
            }
        });
        
        ((ImageButton) findViewById(R.id.button_vk)).setOnClickListener(new OnClickListener() {
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
        
        ((ImageButton) findViewById(R.id.button_discord)).setOnClickListener(new OnClickListener() {
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
        
        ((ImageButton) findViewById(R.id.button_telegram)).setOnClickListener(new OnClickListener() {
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
        
        ((AppCompatButton) findViewById(R.id.button_clean_game)).setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
            	v.startAnimation(animation);
        	    Timer t = new Timer();
                t.schedule(new TimerTask(){
                   @Override
                   public void run() {
                       ToLoad();
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
                                        //Toast.makeText(this, "Ваш новый никнейм успешно сохранен!", Toast.LENGTH_SHORT).show();
                                        tost("Nama karakter baru berhasil disimpan!");
                                     reg.setNick(String.valueOf(nickname.getText()));
								 } else {
									 checkValidNick();
								 }
                                        w.store();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        tost("Silakan instal game terlebih dahulu!");
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
     }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 1000) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 1000);
        } 
    }

    public void onClickPlay() {
        if (!AlyaLiteDownloader.hasStoragePermission(this)) {
            AlyaLiteDownloader.requestAllFilesPermissionIfNeeded(this);
            return;
        }
        String curNick = nickname != null ? nickname.getText().toString() : reg.getNick();
        AlyaLiteDownloader.ensureClientConfigAndFonts(curNick);
        if (serverAddress != null) {
            String rawServer = serverAddress.getText().toString().trim();
            if (!rawServer.isEmpty()) {
                AlyaLiteDownloader.saveServerConfig(rawServer, null);
            }
        }
        if (IsGameInstalled()) {
            startActivity(new Intent(this, com.byparad1st.game.core.GTASA.class));
        } else {
            ToLoad();
        }
    }
    
    private boolean IsGameInstalled()
    {
        String CheckFile = Environment.getExternalStorageDirectory() + "/ryznSAMP/texdb/gta3.img";
        File file = new File(CheckFile);
        return file.exists();
    }
    
    private void ToLoad()
    {
    	startActivity(new Intent(this, LoaderActivity.class));
    }
	
	private void InitLogic() {
        try {
            Wini w = new Wini(new File(Environment.getExternalStorageDirectory() + "/ryznSAMP/SAMP/settings.ini"));
            nickname = findViewById(R.id.edit_text_name);
            nickname.setText(w.get("client", "name"));
            w.store();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	public boolean checkValidNick(){
        EditText nick = (EditText) findViewById(R.id.edit_text_name);
		if(nick.getText().toString().isEmpty()) {
            tost("Masukkan nama karakter");
			return false;
		}
		if(!(nick.getText().toString().contains("_"))){
            tost("Nama karakter harus mengandung simbol \"_\"");
			return false;
		}
		if(nick.getText().toString().length() < 4){
            tost("Panjang nama karakter minimal 4 karakter");
			return false;
		}
		return true;
	}

    private void LoadNick() {
        try {
            Wini w = new Wini(new File(Environment.getExternalStorageDirectory() + "/ryznSAMP/SAMP/settings.ini"));
            reg.setNick(w.get("client", "name"));
            w.store();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	private void tost(String pon)
	{
		Toast.makeText(this, pon, Toast.LENGTH_SHORT).show();
	}
} 