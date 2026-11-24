package com.byparad1st.game.gui;

import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import com.akexorcist.roundcornerprogressbar.RoundCornerProgressBar;
import com.byparad1st.game.R;
import com.byparad1st.game.gui.util.Utils;
import com.byparad1st.launcher.model.Servers;
import com.byparad1st.launcher.other.AlyaLiteDownloader;
import com.byparad1st.launcher.other.Interface;
import com.nvidia.devtech.NvEventQueueActivity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ChooseServer {

    FrameLayout serverLayout;
    LinearLayout chooseServerLayout;
    LinearLayout loadingLayout;
    ArrayList<Servers> mServers;
    TextView percentText;
    Activity aactivity;
    int type, size;
    TextView mProgress;
    LinearLayout linearLayout2;
    View findViewById, findViewById2;
    Button myButton, allButton;
    EditText ipInput, passInput;
    boolean uiInitialized = false;
    RoundCornerProgressBar progressbar;

    public ChooseServer(Activity activity){
        aactivity = activity;
        serverLayout = activity.findViewById(R.id.br_serverselect_layout);
        percentText = activity.findViewById(R.id.loading_percent);
        chooseServerLayout = activity.findViewById(R.id.choose_server_root_choose);
        loadingLayout = activity.findViewById(R.id.choose_server_root_loading);
        type = NvEventQueueActivity.getInstance().getLastServer();
        findViewById = aactivity.findViewById(R.id.main_servers_choose);
        findViewById2 = aactivity.findViewById(R.id.list_servers_choose);
        myButton = aactivity.findViewById(R.id.servers_btn_my_server);
        allButton = aactivity.findViewById(R.id.all_servers_button);
        ipInput = aactivity.findViewById(R.id.br_server_ip_input);
        passInput = aactivity.findViewById(R.id.br_server_pass_input);
        progressbar = aactivity.findViewById(R.id.progress_bar);
        mServers = new ArrayList<Servers>();
        
        Retrofit retrofit = new Retrofit.Builder()
				.baseUrl("http://vbd.fdv.dd/")
				.addConverterFactory(GsonConverterFactory.create())
				.build();

		Interface sInterface = retrofit.create(Interface.class);

		Call<List<Servers>> scall = sInterface.getServers();

		scall.enqueue(new Callback<List<Servers>>() {
			@Override
			public void onResponse(Call<List<Servers>> call, Response<List<Servers>> response) {
				List<Servers> servers = response.body();
				if (servers == null) {
					return;
				}
				mServers.clear();
				for (Servers server : servers) {
					if (server != null) {
						mServers.add(new Servers(
								server.getColor(),
								server.getDopname(),
								server.getname(),
								server.getOnline(),
								server.getmaxOnline(),
								server.getIp(),
								server.getPort()));
					}
				}
				if (uiInitialized) {
					aactivity.runOnUiThread(() -> populateServerCards());
				}
			}

			@Override
			public void onFailure(Call<List<Servers>> call, Throwable t) {
				Toast.makeText(activity, "Mode sambung langsung aktif: masukkan IP:Port server apa saja", Toast.LENGTH_SHORT).show();
			}
		});
		
        Utils.HideLayout(serverLayout, false);
    }

    private int parseColorSafe(String hexColor) {
        if (hexColor == null || hexColor.trim().isEmpty()) {
            return Color.parseColor("#FF0000");
        }
        try {
            String clean = hexColor.trim();
            if (!clean.startsWith("#")) {
                clean = "#" + clean;
            }
            return Color.parseColor(clean);
        } catch (IllegalArgumentException ignored) {
            return Color.parseColor("#FF0000");
        }
    }

    private void connectToServer(String rawAddress, String password) {
        String addr = rawAddress != null ? rawAddress.trim() : "";
        if (addr.isEmpty() && type >= 0 && type < mServers.size()) {
            Servers fallback = mServers.get(type);
            addr = AlyaLiteDownloader.formatServerAddress(fallback.getIp(), fallback.getPort());
        }
        String[] parsed = AlyaLiteDownloader.parseServerAddress(addr);
        String host = parsed[0];
        if (host.isEmpty()) {
            Toast.makeText(aactivity, "Masukkan IP:Port server SA-MP terlebih dahulu!", Toast.LENGTH_SHORT).show();
            return;
        }
        int port = AlyaLiteDownloader.DEFAULT_SERVER_PORT;
        try {
            port = Integer.parseInt(parsed[1]);
        } catch (NumberFormatException ignored) {
        }
        String pass = password != null ? password : "";
        AlyaLiteDownloader.saveServerConfig(host + ":" + port, pass);
        String payload = host + ":" + port + "|" + pass;
        NvEventQueueActivity.getInstance().sendRPC(2, payload.getBytes(StandardCharsets.UTF_8), port);
        Utils.HideLayout(serverLayout, true);
    }

    private void bindMainCard() {
        String savedServer = AlyaLiteDownloader.getSavedServerAddress();
        if (ipInput != null && !savedServer.isEmpty() && ipInput.getText().toString().trim().isEmpty()) {
            ipInput.setText(savedServer);
        }
        String savedPass = AlyaLiteDownloader.getSavedServerPassword();
        if (passInput != null && !savedPass.isEmpty() && passInput.getText().toString().isEmpty()) {
            passInput.setText(savedPass);
        }

        TextView serverNameView = aactivity.findViewById(R.id.br_server_name);
        TextView serverOnlineView = aactivity.findViewById(R.id.br_server_online);
        View backColorView = aactivity.findViewById(R.id.server_list_back_color);
        RoundCornerProgressBar roundProgress = aactivity.findViewById(R.id.br_server_progress);
        ImageView serverImage = aactivity.findViewById(R.id.br_server_image);

        if (type >= 0 && type < mServers.size()) {
            Servers s = mServers.get(type);
            int color = parseColorSafe(s.getColor());
            int maxOnline = Math.max(1, s.getmaxOnline());
            int pct = (int) Math.min(100, Math.round((s.getOnline() * 100.0) / maxOnline));
            if (ipInput != null && ipInput.getText().toString().trim().isEmpty() && !s.getIp().isEmpty()) {
                ipInput.setText(AlyaLiteDownloader.formatServerAddress(s.getIp(), s.getPort()));
            }
            if (serverNameView != null) {
                serverNameView.setText(!savedServer.isEmpty() ? savedServer : s.getname());
            }
            if (backColorView != null && backColorView.getBackground() != null) {
                backColorView.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
            if (roundProgress != null) {
                roundProgress.setProgressColor(color);
                roundProgress.setProgress(pct);
            }
            if (serverImage != null) {
                serverImage.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
            if (serverOnlineView != null) {
                serverOnlineView.setText(Html.fromHtml(s.getOnline() + "<font color='#808080'>/" + s.getmaxOnline()));
            }
        } else {
            int color = parseColorSafe("#1E88E5");
            if (serverNameView != null) {
                serverNameView.setText(!savedServer.isEmpty() ? ("Target: " + savedServer) : "Server Bebas (SA-MP 0.3.7)");
            }
            if (backColorView != null && backColorView.getBackground() != null) {
                backColorView.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
            if (roundProgress != null) {
                roundProgress.setProgressColor(color);
                roundProgress.setProgress(100);
            }
            if (serverImage != null) {
                serverImage.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
            if (serverOnlineView != null) {
                serverOnlineView.setText(!savedServer.isEmpty() ? "Siap sambung ke server pilihan" : "Masukkan IP:Port server di bawah");
            }
        }
    }

    private void populateServerCards() {
        bindMainCard();
        LinearLayout linearLayout = aactivity.findViewById(R.id.scroll_layout_servers);
        if (linearLayout == null) {
            return;
        }
        linearLayout.removeAllViews();
        linearLayout.setScrollbarFadingEnabled(false);

        if (mServers.isEmpty()) {
            return;
        }

        int rows = ((mServers.size() - 1) / 4) + 1;
        for (int i4 = 0; i4 < rows; i4++) {
            linearLayout2 = new LinearLayout(aactivity);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i4 < rows - 1) {
                layoutParams.setMargins(0, 0, 0, NvEventQueueActivity.dpToPx(12.0f, aactivity));
            }
            linearLayout2.setLayoutParams(layoutParams);
            linearLayout2.setOrientation(LinearLayout.HORIZONTAL);

            for (int i5 = 0; i5 < 4; i5++) {
                int i6 = (i4 * 4) + i5;
                if (i6 >= mServers.size()) {
                    break;
                }
                final int idx = (mServers.size() - i6) - 1;
                Servers srv = mServers.get(idx);
                View inflate = ((LayoutInflater) aactivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.br_serverselect_server, null, false);
                inflate.setOnClickListener(view -> {
                    view.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
                    String target = AlyaLiteDownloader.formatServerAddress(srv.getIp(), srv.getPort());
                    if (!target.isEmpty() && ipInput != null) {
                        ipInput.setText(target);
                    }
                    String pass = passInput != null ? passInput.getText().toString() : "";
                    connectToServer(target, pass);
                });
                ((TextView) inflate.findViewById(R.id.br_server_name)).setText(srv.getname());
                LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(NvEventQueueActivity.dpToPx(160.0f, aactivity), NvEventQueueActivity.dpToPx(80.0f, aactivity));
                layoutParams2.weight = 0.25f;
                if (i5 < 3) {
                    layoutParams2.setMargins(0, 0, NvEventQueueActivity.dpToPx(20.0f, aactivity), 0);
                }
                inflate.setLayoutParams(layoutParams2);
                int color = parseColorSafe(srv.getColor());
                View backColor3 = inflate.findViewById(R.id.server_list_back_color);
                RoundCornerProgressBar roundCornerProgressBar = inflate.findViewById(R.id.br_server_progress);
                ImageView imageView = inflate.findViewById(R.id.br_server_image);
                if (backColor3 != null && backColor3.getBackground() != null) {
                    backColor3.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                }
                if (roundCornerProgressBar != null) {
                    roundCornerProgressBar.setProgressColor(color);
                    int maxOnline = Math.max(1, srv.getmaxOnline());
                    roundCornerProgressBar.setProgress((float) ((int) ((srv.getOnline() * 100.0d) / maxOnline)));
                }
                if (imageView != null) {
                    imageView.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                }
                ((TextView) inflate.findViewById(R.id.br_server_online)).setText(Html.fromHtml(srv.getOnline() + "<font color='#808080'>/" + srv.getmaxOnline()));
                linearLayout2.addView(inflate);
            }
            linearLayout.addView(linearLayout2);
        }
    }

    public void Update(int percent) {
        if (percent <= 100) {
            percentText.setText(new Formatter().format("%d%s", Integer.valueOf(percent), "%").toString());
            progressbar.setProgress(percent);
        }
        else {
            chooseServerLayout.setVisibility(View.VISIBLE);
            chooseServerLayout.setAlpha(0.0f);
            chooseServerLayout.animate().setDuration(2000).alpha(1.0f).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }

                @Override
                public void onAnimationStart(Animator animator) {
                    initUi();
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    loadingLayout.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    public void initUi() {
        uiInitialized = true;
        findViewById2.setAlpha(0.0f);
        findViewById2.setVisibility(View.GONE);
        findViewById.setAlpha(1.0f);
        findViewById.setVisibility(View.VISIBLE);
        myButton.setVisibility(View.VISIBLE);
        allButton.setVisibility(View.VISIBLE);

        mProgress = aactivity.findViewById(R.id.loading_percent);
        aactivity.findViewById(R.id.server_main_frame).setOnClickListener(view -> {
            view.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
        });
        aactivity.findViewById(R.id.br_servers_play).setOnClickListener(view -> {
            String rawIp = ipInput != null ? ipInput.getText().toString() : "";
            String rawPass = passInput != null ? passInput.getText().toString() : "";
            connectToServer(rawIp, rawPass);
        });

        myButton.setOnClickListener(view -> {
            view.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
            view.setBackground(ResourcesCompat.getDrawable(aactivity.getResources(), R.drawable.alyn_btn_primary, null));
            allButton.setBackground(ResourcesCompat.getDrawable(aactivity.getResources(), R.drawable.alyn_btn_secondary, null));
            if (findViewById.getVisibility() != View.VISIBLE) {
                findViewById2.animate().alpha(0.0f).setDuration(100).setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {

                    }
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        findViewById2.setAlpha(0.0f);
                        findViewById2.setVisibility(View.GONE);
                        findViewById.setVisibility(View.VISIBLE);
                        findViewById.setAlpha(0.0f);
                        findViewById.animate().alpha(1.0f).setDuration(100).setListener(null).start();
                    }
                    @Override
                    public void onAnimationCancel(Animator animator) {

                    }
                    @Override
                    public void onAnimationRepeat(Animator animator) {

                    }
                });
            }
        });
        allButton.setOnClickListener(view -> {
            view.startAnimation(AnimationUtils.loadAnimation(aactivity, R.anim.button_click));
            view.setBackground(ResourcesCompat.getDrawable(aactivity.getResources(), R.drawable.alyn_btn_primary, null));
            myButton.setBackground(ResourcesCompat.getDrawable(aactivity.getResources(), R.drawable.alyn_btn_secondary, null));
            if (findViewById2.getVisibility() != View.VISIBLE) {
                findViewById.animate().alpha(0.0f).setDuration(100).setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {

                    }
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        findViewById.setAlpha(0.0f);
                        findViewById.setVisibility(View.GONE);
                        findViewById2.setVisibility(View.VISIBLE);
                        findViewById2.setAlpha(0.0f);
                        findViewById2.animate().alpha(1.0f).setDuration(100).setListener(null).start();
                    }
                    @Override
                    public void onAnimationCancel(Animator animator) {

                    }
                    @Override
                    public void onAnimationRepeat(Animator animator) {

                    }
                });
            }
        });

        populateServerCards();
    }

    public void Show() {
        Utils.ShowLayout(serverLayout, false);
    }

}