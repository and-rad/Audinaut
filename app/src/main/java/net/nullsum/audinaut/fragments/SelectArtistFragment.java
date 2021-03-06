package net.nullsum.audinaut.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import net.nullsum.audinaut.R;
import net.nullsum.audinaut.adapter.ArtistAdapter;
import net.nullsum.audinaut.adapter.SectionAdapter;
import net.nullsum.audinaut.domain.Artist;
import net.nullsum.audinaut.domain.Indexes;
import net.nullsum.audinaut.domain.MusicDirectory;
import net.nullsum.audinaut.domain.MusicDirectory.Entry;
import net.nullsum.audinaut.domain.MusicFolder;
import net.nullsum.audinaut.service.MusicService;
import net.nullsum.audinaut.util.Constants;
import net.nullsum.audinaut.util.ProgressListener;
import net.nullsum.audinaut.util.Util;
import net.nullsum.audinaut.view.UpdateView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SelectArtistFragment extends SelectRecyclerFragment<Serializable> implements ArtistAdapter.OnMusicFolderChanged {

    private List<MusicFolder> musicFolders = null;
    private List<Entry> entries;
    private String groupId;
    private String groupName;

    public SelectArtistFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        if (bundle != null) {
            musicFolders = (List<MusicFolder>) bundle.getSerializable(Constants.FRAGMENT_LIST2);
        }
        artist = true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(Constants.FRAGMENT_LIST2, (Serializable) musicFolders);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Bundle args = getArguments();
        if (args != null) {
            if (args.getBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, false)) {
                groupId = args.getString(Constants.INTENT_EXTRA_NAME_ID);
                groupName = args.getString(Constants.INTENT_EXTRA_NAME_NAME);

                if (groupName != null) {
                    setTitle(groupName);
                    context.invalidateOptionsMenu();
                }
            }
        }

        super.onCreateView(inflater, container, bundle);

        return rootView;
    }

    @Override
    public void onCreateContextMenu(Menu menu, MenuInflater menuInflater, UpdateView<Serializable> updateView, Serializable item) {
        onCreateContextMenuSupport(menu, menuInflater, updateView, item);
        recreateContextMenu(menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem, UpdateView<Serializable> updateView, Serializable item) {
        return onContextItemSelected(menuItem, item);
    }

    @Override
    public void onItemClicked(UpdateView<Serializable> updateView, Serializable item) {
        SubsonicFragment fragment;
        if (item instanceof Artist) {
            Artist artist = (Artist) item;

            if ((Util.isFirstLevelArtist(context) || Util.isOffline(context)) || groupId != null) {
                fragment = new SelectDirectoryFragment();
                Bundle args = new Bundle();
                args.putString(Constants.INTENT_EXTRA_NAME_ID, artist.getId());
                args.putString(Constants.INTENT_EXTRA_NAME_NAME, artist.getName());

                if (!Util.isOffline(context)) {
                    args.putSerializable(Constants.INTENT_EXTRA_NAME_DIRECTORY, new Entry(artist));
                }
                args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);

                fragment.setArguments(args);
            } else {
                fragment = new SelectArtistFragment();
                Bundle args = new Bundle();
                args.putString(Constants.INTENT_EXTRA_NAME_ID, artist.getId());
                args.putString(Constants.INTENT_EXTRA_NAME_NAME, artist.getName());
                args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
                if (!Util.isOffline(context)) {
                    args.putSerializable(Constants.INTENT_EXTRA_NAME_DIRECTORY, new Entry(artist));
                }

                fragment.setArguments(args);
            }

            replaceFragment(fragment);
        } else {
            Entry entry = (Entry) item;
            onSongPress(entries, entry);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);

        if (Util.isOffline(context) || groupId != null) {
            menu.removeItem(R.id.menu_first_level_artist);
        } else {
            if (Util.isFirstLevelArtist(context)) {
                menu.findItem(R.id.menu_first_level_artist).setChecked(true);
            }
        }
    }

    @Override
    public int getOptionsMenu() {
        return R.menu.select_artist;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.menu_first_level_artist:
                toggleFirstLevelArtist();
                break;
        }

        return false;
    }

    @Override
    public SectionAdapter getAdapter(List<Serializable> objects) {
        return new ArtistAdapter(context, objects, musicFolders, this, this);
    }

    @Override
    public List<Serializable> getObjects(MusicService musicService, boolean refresh, ProgressListener listener) throws Exception {
        List<Serializable> items;
        if (groupId == null) {
            musicFolders = null;
            String musicFolderId = Util.getSelectedMusicFolderId(context);

            Indexes indexes = musicService.getIndexes(musicFolderId, refresh, context, listener);
            indexes.sortChildren();
            items = new ArrayList<>(indexes.getShortcuts().size() + indexes.getArtists().size());
            items.addAll(indexes.getShortcuts());
            items.addAll(indexes.getArtists());
            entries = indexes.getEntries();
            items.addAll(entries);
        } else {
            MusicDirectory dir = musicService.getMusicDirectory(groupId, groupName, refresh, context, listener);
            for (Entry entry : dir.getChildren(true, false)) {
                Artist artist = new Artist();
                artist.setId(entry.getId());
                artist.setName(entry.getTitle());
            }

            Indexes indexes = new Indexes();
            indexes.sortChildren();
            items = new ArrayList<>(indexes.getArtists());

            entries = dir.getChildren(false, true);
            items.addAll(entries);
        }

        return items;
    }

    @Override
    public int getTitleResource() {
        return groupId == null ? R.string.button_bar_browse : 0;
    }

    @Override
    public void setEmpty(boolean empty) {
        super.setEmpty(empty);

        if (empty && !Util.isOffline(context)) {
            objects.clear();
            recyclerView.setAdapter(new ArtistAdapter(context, objects, musicFolders, this, this));
            recyclerView.setVisibility(View.VISIBLE);

            View view = rootView.findViewById(R.id.tab_progress);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
            params.height = 0;
            params.weight = 5;
            view.setLayoutParams(params);
        }
    }

    private void toggleFirstLevelArtist() {
        Util.toggleFirstLevelArtist(context);
        context.invalidateOptionsMenu();
    }

    @Override
    public void onMusicFolderChanged(MusicFolder selectedFolder) {
        String startMusicFolderId = Util.getSelectedMusicFolderId(context);
        String musicFolderId = selectedFolder == null ? null : selectedFolder.getId();

        if (!Util.equals(startMusicFolderId, musicFolderId)) {
            Util.setSelectedMusicFolderId(context, musicFolderId);
            context.invalidate();
        }
    }
}
