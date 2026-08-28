package com.winlator.onlineconfig;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.winlator.R;
import com.winlator.container.ContainerManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class OnlineConfigsFragment extends androidx.fragment.app.Fragment {
    private final ArrayList<OnlineConfig> configs = new ArrayList<>();
    private final ArrayList<GameGroup> games = new ArrayList<>();
    private EditText search;
    private TextView status;
    private TextView empty;
    private RecyclerView list;
    private OnlineConfigAdapter adapter;
    private OnlineConfigRepository repository;
    private boolean showingConfigs;
    private String selectedGameId = "";

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.online_configs_fragment, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        search=view.findViewById(R.id.ETOnlineSearch); status=view.findViewById(R.id.TVOnlineStatus); empty=view.findViewById(R.id.TVOnlineEmpty); list=view.findViewById(R.id.RVOnlineConfigs);
        list.setLayoutManager(new LinearLayoutManager(requireContext())); adapter=new OnlineConfigAdapter(); list.setAdapter(adapter);
        AppCompatActivity activity=(AppCompatActivity)requireActivity(); activity.getSupportActionBar().setTitle(R.string.online_game_configs); activity.getSupportActionBar().setHomeAsUpIndicator(R.drawable.icon_action_bar_back);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){adapter.notifyDataSetChanged();}public void afterTextChanged(Editable e){}});
        ((ImageButton)view.findViewById(R.id.BTOnlineRefresh)).setOnClickListener(v->load()); load();
    }

    private void load() {
        status.setText(R.string.loading); status.setVisibility(View.VISIBLE); empty.setVisibility(View.GONE);
        repository=new OnlineConfigRepository(requireContext()); repository.refresh(items->requireActivity().runOnUiThread(()->{configs.clear();configs.addAll(items);games.clear();Map<String,GameGroup> grouped=new LinkedHashMap<>();for(OnlineConfig c:configs){GameGroup g=grouped.get(c.gameId);if(g==null){g=new GameGroup(c.gameId,c.gameName);grouped.put(c.gameId,g);}g.configs.add(c);}games.addAll(grouped.values());Collections.sort(games,(a,b)->a.name.compareToIgnoreCase(b.name));showingConfigs=false;adapter.notifyDataSetChanged();status.setText(items.isEmpty()?R.string.no_online_configs:(repository.isUsingCache()?R.string.using_cached_data:R.string.online_configs_found));empty.setVisibility(items.isEmpty()?View.VISIBLE:View.GONE);}));
    }

    public boolean onNavigateUp(){if(showingConfigs){showingConfigs=false;((AppCompatActivity)requireActivity()).getSupportActionBar().setTitle(R.string.online_game_configs);adapter.notifyDataSetChanged();return true;}return false;}
    private String query(){return search==null?"":search.getText().toString().trim().toLowerCase();}

    private final class OnlineConfigAdapter extends RecyclerView.Adapter<OnlineConfigHolder>{
        @NonNull @Override public OnlineConfigHolder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new OnlineConfigHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.online_config_item,parent,false));}
        @Override public void onBindViewHolder(@NonNull OnlineConfigHolder holder,int position){if(!showingConfigs){GameGroup game=filteredGames().get(position);holder.title.setText(game.name);holder.subtitle.setText(countLabel(game.configs.size()));holder.itemView.setOnClickListener(v->{selectedGameId=game.id;showingConfigs=true;((AppCompatActivity)requireActivity()).getSupportActionBar().setTitle(game.name);adapter.notifyDataSetChanged();});}else{OnlineConfig config=filteredConfigs().get(position);holder.title.setText(config.getDisplayTitle());holder.subtitle.setText(summary(config));holder.itemView.setOnClickListener(v->OnlineConfigBrowserDialog.showConfigDetails((AppCompatActivity)requireActivity(),new ContainerManager(requireContext()),config));}}
        @Override public int getItemCount(){return showingConfigs?filteredConfigs().size():filteredGames().size();}
        private String summary(OnlineConfig c){return c.settings.optString("screenSize",getString(R.string.not_specified))+" · "+c.settings.optString("graphicsDriver",getString(R.string.not_specified))+" · "+OnlineConfigCompatibility.label(requireContext(),c);}
        private String countLabel(int count){if(count==1&&java.util.Locale.getDefault().getLanguage().equals("en"))return getString(R.string.online_setting_singular);return getString(R.string.online_config_count,count);}
    }
    private ArrayList<GameGroup> filteredGames(){ArrayList<GameGroup> out=new ArrayList<>();String q=query();for(GameGroup g:games)if(q.isEmpty()||g.name.toLowerCase().contains(q)||g.id.toLowerCase().contains(q))out.add(g);return out;}
    private ArrayList<OnlineConfig> filteredConfigs(){ArrayList<OnlineConfig> out=new ArrayList<>();String q=query();for(OnlineConfig c:configs)if(c.gameId.equals(selectedGameId)&&(q.isEmpty()||c.gameName.toLowerCase().contains(q)||c.getDisplayTitle().toLowerCase().contains(q)))out.add(c);return out;}
    private static final class GameGroup{final String id,name;final ArrayList<OnlineConfig> configs=new ArrayList<>();GameGroup(String id,String name){this.id=id;this.name=name;}}
    private static final class OnlineConfigHolder extends RecyclerView.ViewHolder{final TextView title,subtitle;OnlineConfigHolder(View view){super(view);title=view.findViewById(R.id.TVOnlineItemTitle);subtitle=view.findViewById(R.id.TVOnlineItemSubtitle);}}
}
