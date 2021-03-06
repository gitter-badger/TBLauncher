package rocks.tbog.tblauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import net.mm2d.color.chooser.ViewPagerAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import kotlin.Pair;
import rocks.tbog.tblauncher.dataprovider.ActionProvider;
import rocks.tbog.tblauncher.dataprovider.FilterProvider;
import rocks.tbog.tblauncher.dataprovider.QuickListProvider;
import rocks.tbog.tblauncher.dataprovider.TagsProvider;
import rocks.tbog.tblauncher.db.FavRecord;
import rocks.tbog.tblauncher.entry.EntryItem;
import rocks.tbog.tblauncher.entry.StaticEntry;
import rocks.tbog.tblauncher.entry.TagEntry;
import rocks.tbog.tblauncher.result.ResultHelper;

public class EditQuickList {

    private final ArrayList<EntryItem> mQuickList = new ArrayList<>();
    private LinearLayout mQuickListContainer;
    ViewPager mViewPager;
    private SharedPreferences mPref;
    private final EntryAdapter.OnItemClickListener mAddToQuickList = (adapter, view, pos) -> {
        mQuickList.add(adapter.getItem(pos));
        populateList();
    };

    public void applyChanges(@NonNull Context context) {
        ArrayList<String> idList = new ArrayList<>(mQuickList.size());
        for (EntryItem entry : mQuickList)
            idList.add(entry.id);
        TBApplication.dataHandler(context).setQuickList(idList);
    }

    public void bindView(@NonNull View view) {
        final Context context = view.getContext();
        // keep the preview the same as the actual thing
        mQuickListContainer = view.findViewById(R.id.preview);
        {
            QuickListProvider provider = TBApplication.dataHandler(context).getQuickListProvider();
            List<? extends EntryItem> list = provider != null ? provider.getPojos() : null;
            if (list != null)
                mQuickList.addAll(list);
        }
        mPref = PreferenceManager.getDefaultSharedPreferences(context);
        QuickList.applyUiPref(mPref, mQuickListContainer);
        populateList();

        mViewPager = view.findViewById(R.id.viewPager);
        {
            ArrayList<Pair<String, View>> pages = new ArrayList<>();
            LayoutInflater inflater = LayoutInflater.from(context);

            // filters
            {
                GridView gridView = (GridView) inflater.inflate(R.layout.quick_list_editor_page, mViewPager, false);
                pages.add(new Pair<>(context.getString(R.string.edit_quick_list_tab_filters), gridView));

                ArrayList<EntryItem> list = new ArrayList<>();
                EntryAdapter adapter = new EntryAdapter(list);
                gridView.setAdapter(adapter);
                new LoadDataForAdapter(adapter, () -> {
                    Context ctx = gridView.getContext();
                    ArrayList<EntryItem> data = new ArrayList<>();
                    {
                        List<? extends EntryItem> entryItems = new FilterProvider(ctx).getPojos();
                        data.addAll(entryItems);
                    }
                    return data;
                }).execute();
                adapter.setOnItemClickListener(mAddToQuickList);
            }

            // actions
            {
                GridView gridView = (GridView) inflater.inflate(R.layout.quick_list_editor_page, mViewPager, false);
                pages.add(new Pair<>(context.getString(R.string.edit_quick_list_tab_actions), gridView));

                ArrayList<EntryItem> list = new ArrayList<>();
                EntryAdapter adapter = new EntryAdapter(list);
                gridView.setAdapter(adapter);
                new LoadDataForAdapter(adapter, () -> {
                    Context ctx = gridView.getContext();
                    ArrayList<EntryItem> data = new ArrayList<>();
                    {
                        List<? extends EntryItem> entryItems = new ActionProvider(ctx).getPojos();
                        data.addAll(entryItems);
                    }
                    return data;
                }).execute();
                adapter.setOnItemClickListener(mAddToQuickList);
            }

            // tags
            {
                GridView gridView = (GridView) inflater.inflate(R.layout.quick_list_editor_page, mViewPager, false);
                pages.add(new Pair<>(context.getString(R.string.edit_quick_list_tab_tags), gridView));

                ArrayList<EntryItem> list = new ArrayList<>();
                EntryAdapter adapter = new EntryAdapter(list);
                gridView.setAdapter(adapter);
                new LoadDataForAdapter(adapter, () -> {
                    Context ctx = gridView.getContext();
                    ArrayList<EntryItem> data = new ArrayList<>();
                    {
                        TagsProvider tagsProvider = TBApplication.dataHandler(ctx).getTagsProvider();
                        if (tagsProvider != null) {
                            List<String> tagNameList = new ArrayList<>(TBApplication.tagsHandler(ctx).getValidTags());
                            Collections.sort(tagNameList);
                            for (String tagName : tagNameList) {
                                TagEntry tagEntry = tagsProvider.getTagEntry(tagName);
                                data.add(tagEntry);
                            }
                        }
                    }
                    return data;
                }).execute();
                adapter.setOnItemClickListener(mAddToQuickList);
            }

            // favorites
            {
                GridView gridView = (GridView) inflater.inflate(R.layout.quick_list_editor_page, mViewPager, false);
                pages.add(new Pair<>(context.getString(R.string.edit_quick_list_tab_favorites), gridView));

                ArrayList<EntryItem> list = new ArrayList<>();
                EntryAdapter adapter = new EntryAdapter(list);
                gridView.setAdapter(adapter);
                new LoadDataForAdapter(adapter, () -> {
                    Context ctx = gridView.getContext();
                    DataHandler dataHandler = TBApplication.dataHandler(ctx);
                    ArrayList<FavRecord> favRecords = dataHandler.getFavorites();
                    ArrayList<EntryItem> data = new ArrayList<>(favRecords.size());
                    for (FavRecord fav : favRecords) {
                        EntryItem entry = dataHandler.getPojo(fav.record);
                        // we have a separate section for StaticEntry (filters and actions), don't duplicate
                        if (entry != null && !(entry instanceof StaticEntry))
                            data.add(entry);
                    }
                    return data;
                }).execute();
                adapter.setOnItemClickListener(mAddToQuickList);
            }
            pages.trimToSize();
            mViewPager.setAdapter(new ViewPagerAdapter(pages));
        }
        {
            TabLayout tabLayout = mViewPager.findViewById(R.id.tabLayout);
            tabLayout.setupWithViewPager(mViewPager);
        }

        {
            CustomizeUI customizeUI = TBApplication.ui(context);
            customizeUI.setResultListPref(mViewPager);
        }
    }

    private void populateList() {
        Context context = mQuickListContainer.getContext();

        mQuickListContainer.removeAllViews();
        int drawFlags = QuickList.getDrawFlags(mPref) | EntryItem.FLAG_DRAW_NO_CACHE;
        for (EntryItem entry : mQuickList) {
            View view = LayoutInflater.from(context).inflate(entry.getResultLayout(drawFlags), mQuickListContainer, false);
            entry.displayResult(view, drawFlags);
            mQuickListContainer.addView(view);

            // when user clicks, remove the view and the list item
            view.setOnClickListener((v) -> {
                if (v.getParent() instanceof ViewGroup) {
                    int idx = ((ViewGroup) v.getParent()).indexOfChild(v);
                    ((ViewGroup) v.getParent()).removeViewAt(idx);
                    mQuickList.remove(idx);
                }
            });
        }
        mQuickListContainer.requestLayout();
    }

    static class LoadDataForAdapter extends AsyncTask<Void, Void, ArrayList<EntryItem>> {
        private final EntryAdapter adapter;
        private final LoadInBackground task;

        interface LoadInBackground {
            ArrayList<EntryItem> loadInBackground();
        }

        public LoadDataForAdapter(EntryAdapter adapter, LoadInBackground loadInBackground) {
            super();
            this.adapter = adapter;
            task = loadInBackground;
        }

        @Override
        protected ArrayList<EntryItem> doInBackground(Void... voids) {
            ArrayList<EntryItem> data = task.loadInBackground();
            data.trimToSize();
            return data;
        }

        @Override
        protected void onPostExecute(ArrayList<EntryItem> data) {
            if (data == null)
                return;
            adapter.addAll(data);
        }
    }

    static class EntryAdapter extends BaseAdapter {
        private final List<EntryItem> mItems;
        private EntryAdapter.OnItemClickListener mOnItemClickListener = null;

        public interface OnItemClickListener {
            void onItemClick(EntryAdapter adapter, View view, int position);
        }

        EntryAdapter(@NonNull List<EntryItem> objects) {
            mItems = objects;
        }

        void setOnItemClickListener(EntryAdapter.OnItemClickListener listener) {
            mOnItemClickListener = listener;
        }

        void addAll(Collection<EntryItem> newElements) {
            mItems.addAll(newElements);
            notifyDataSetChanged();
        }

        @Override
        public int getViewTypeCount() {
            return ResultHelper.getItemViewTypeCount();
        }

        @Override
        public int getItemViewType(int position) {
            return ResultHelper.getItemViewType(mItems.get(position));
        }

        @Override
        public EntryItem getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            int drawFlags = EntryItem.FLAG_DRAW_GRID | EntryItem.FLAG_DRAW_NAME | EntryItem.FLAG_DRAW_ICON | EntryItem.FLAG_DRAW_ICON_BADGE;
            final View view;
            EntryItem content = getItem(position);
            if (convertView == null) {
                view = LayoutInflater.from(parent.getContext()).inflate(content.getResultLayout(drawFlags), parent, false);
            } else {
                view = convertView;
            }

            content.displayResult(view, drawFlags);

            view.setOnClickListener(v -> {
                if (mOnItemClickListener != null)
                    mOnItemClickListener.onItemClick(EntryAdapter.this, v, position);
            });

            return view;
        }
    }
}
