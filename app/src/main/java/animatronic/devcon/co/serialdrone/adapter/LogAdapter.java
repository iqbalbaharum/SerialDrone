package animatronic.devcon.co.serialdrone.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

import animatronic.devcon.co.serialdrone.R;
import animatronic.devcon.co.serialdrone.model.MLog;

/**
 * Created by MuhammadIqbal on 25/4/2017.
 */

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogHolder> {

    private ArrayList<MLog> mData;
    private Context mCtxt;

    public LogAdapter(Context context) {
        mCtxt = context;
        mData = new ArrayList<>();
    }

    @Override
    public LogHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mCtxt).inflate(R.layout.log_view_holder, parent, false);
        return new LogHolder(view);
    }

    @Override
    public void onBindViewHolder(LogHolder holder, int position) {
        MLog log = getData(position);
        holder.tvLog.setText(log.getDescription());
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public void add(MLog log) {
        mData.add(log);
        notifyDataSetChanged();
    }

    public MLog getData(int index) { return mData.get(index); }


    static class LogHolder extends RecyclerView.ViewHolder {

        private TextView tvLog;

        public LogHolder(View itemView) {
            super(itemView);

            tvLog = (TextView) itemView.findViewById(R.id.tv_log);
        }
    }
}
