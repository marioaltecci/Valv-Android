package se.arctosoft.vault;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RecentVaultsAdapter extends RecyclerView.Adapter<RecentVaultsAdapter.ViewHolder> {

    private List<String> vaultPaths;
    private OnVaultClickListener listener;

    public interface OnVaultClickListener {
        void onVaultClick(String path);
    }

    public RecentVaultsAdapter(List<String> vaultPaths, OnVaultClickListener listener) {
        this.vaultPaths = vaultPaths;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String path = vaultPaths.get(position);
        holder.textView.setText(path);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVaultClick(path);
            }
        });
    }

    @Override
    public int getItemCount() {
        return vaultPaths.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }
    }
}
